package com.bakery.user.repository;

import com.bakery.common.TextFileStorage;
import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.AdminUser;
import com.bakery.user.model.CustomerType;
import com.bakery.user.model.LoyaltyCustomer;
import com.bakery.user.model.RegularCustomer;
import com.bakery.user.model.User;
import com.bakery.user.model.UserInputRules;
import com.bakery.user.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-backed store for accounts in {@code users.txt}.
 *
 * <p><b>Current format</b> (one account per line, UTF-8, {@code |}-separated, 10 fields):</p>
 * <pre>id|role|customerType|fullName|email|phone|address|passwordHash|status|createdDate
 * U002|CUSTOMER|REGULAR|Nimal Perera|nimal@example.com|+94 77 123 4567|12 Lake Rd, Kandy|$2a$10$...|ACTIVE|2026-11-10</pre>
 *
 * <p><b>Legacy format</b> (scaffold sample, 4 fields): {@code id|name|email|role}. These records have
 * no credentials, so they are loaded as INACTIVE accounts without a password and can never sign in.
 * The first write after loading a legacy record saves the original file to
 * {@value #BACKUP_FILE_NAME} (once).</p>
 *
 * <p><b>Safety:</b> every operation runs under one lock, so check-then-write sequences
 * (unique email, next ID) cannot interleave. Writes replace the file atomically. Lines that
 * cannot be parsed are never dropped: they are written back unchanged and their IDs are never
 * reused. Fields containing {@code |} or line breaks are refused before writing.</p>
 *
 * <p>Active when {@code bakery.storage.type=file} (also the fallback if the property is missing).</p>
 */
@Repository
@ConditionalOnProperty(name = "bakery.storage.type", havingValue = "file", matchIfMissing = true)
public class FileUserRepository implements UserRepository {

    public static final String FILE_NAME = "users.txt";
    public static final String BACKUP_FILE_NAME = "users-legacy-backup.txt";
    public static final String HEADER =
            "# id|role|customerType|fullName|email|phone|address|passwordHash|status|createdDate";
    private static final String UNREADABLE_NOTE =
            "# Unreadable records kept unchanged below; fix or remove them manually.";
    private static final int FIELD_COUNT = 10;
    private static final int LEGACY_FIELD_COUNT = 4;
    private static final Pattern ID_NUMBER = Pattern.compile("^U(\\d+)$");

    private static final Logger log = LoggerFactory.getLogger(FileUserRepository.class);

    private final TextFileStorage storage;
    private final Object lock = new Object();

    public FileUserRepository(TextFileStorage storage) {
        this.storage = storage;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    /** Read-only result for the TXT → MongoDB migration: readable accounts plus problem counts. */
    public record ReadReport(List<User> users, int unreadableLines, boolean legacyFormatFound) {
    }

    /** Reads the file without changing it and reports how many lines could not be read. */
    public ReadReport readForMigration() {
        synchronized (lock) {
            Snapshot snapshot = load();
            return new ReadReport(List.copyOf(snapshot.users()), snapshot.unreadableLines().size(), snapshot.legacyFound());
        }
    }

    @Override
    public List<User> findAll() {
        synchronized (lock) {
            return List.copyOf(load().users());
        }
    }

    @Override
    public Optional<User> findById(String id) {
        if (id == null) return Optional.empty();
        return findAll().stream().filter(u -> id.equals(u.getId())).findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String normalized = UserInputRules.normalizeEmail(email);
        return findAll().stream().filter(u -> u.getEmail().equals(normalized)).findFirst();
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Saves a new account and assigns the next sequential ID (U001, U002, ...).
     *
     * @throws DuplicateEmailException if the email is already used
     */
    @Override
    public User save(User newUser) {
        synchronized (lock) {
            Snapshot snapshot = load();
            if (newUser.getId() != null) {
                throw new IllegalArgumentException("New accounts must not have an ID yet.");
            }
            requireUniqueEmail(newUser, snapshot.users());
            newUser.assignId(nextId(snapshot));
            List<User> users = new ArrayList<>(snapshot.users());
            users.add(newUser);
            write(snapshot, users);
            return newUser;
        }
    }

    /**
     * Loads the latest copy of an account, applies {@code change} and saves the result,
     * all while holding the lock.
     *
     * @throws IllegalArgumentException if the account does not exist
     * @throws DuplicateEmailException  if the change gives it an email used by another account
     */
    @Override
    public User update(String id, UserChange change) {
        synchronized (lock) {
            Snapshot snapshot = load();
            List<User> users = new ArrayList<>(snapshot.users());
            int index = indexOf(users, id);
            if (index < 0) {
                throw new IllegalArgumentException("Account not found.");
            }
            User updated = change.apply(users.get(index), List.copyOf(users));
            if (updated == null || !id.equals(updated.getId())) {
                throw new IllegalStateException("A change must keep the account's ID.");
            }
            requireUniqueEmail(updated, users);
            users.set(index, updated);
            write(snapshot, users);
            return updated;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private record Snapshot(List<User> users, List<String> keptComments, List<String> unreadableLines,
                            List<String> originalLines, boolean legacyFound) {
    }

    private Snapshot load() {
        List<String> lines;
        try {
            lines = storage.readLines(FILE_NAME);
        } catch (NoSuchFileException e) {
            return new Snapshot(List.of(), List.of(), List.of(), List.of(), false);
        } catch (IOException e) {
            throw new UserStorageException("Could not read account data.", e);
        }

        List<User> users = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> emails = new HashSet<>();
        boolean legacy = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // Files saved by some Windows editors start with a byte-order mark; ignore it.
            if (i == 0 && line.startsWith("﻿")) line = line.substring(1);
            if (line.isBlank()) continue;
            if (line.startsWith("#")) {
                // Regenerated on write: our header, the legacy header and our own note.
                if (!line.startsWith("# id|") && !line.equals(UNREADABLE_NOTE)) comments.add(line);
                continue;
            }
            String[] fields = line.split("\\|", -1);
            User user = null;
            try {
                if (fields.length == FIELD_COUNT) {
                    user = parse(fields);
                } else if (fields.length == LEGACY_FIELD_COUNT) {
                    user = parseLegacy(fields);
                    legacy = true;
                }
            } catch (RuntimeException e) {
                user = null;
            }
            if (user == null || !ids.add(user.getId()) || !emails.add(user.getEmail())) {
                // Log the position only: the line may contain personal data or a password hash.
                log.warn("users.txt line {} could not be read and will be kept unchanged.", i + 1);
                unreadable.add(line);
                continue;
            }
            users.add(user);
        }
        return new Snapshot(users, comments, unreadable, lines, legacy);
    }

    private static User parse(String[] f) {
        String id = f[0].trim();
        UserRole role = UserRole.valueOf(f[1].trim());
        CustomerType type = CustomerType.valueOf(f[2].trim());
        String name = f[3], email = f[4], phone = f[5], address = f[6], hash = f[7].trim();
        AccountStatus status = AccountStatus.valueOf(f[8].trim());
        LocalDate created = LocalDate.parse(f[9].trim());

        // Polymorphic construction: the stored role/type pair decides the subclass.
        return UserFactory.create(id, role, type, name, email, phone, address, hash, status, created);
    }

    /** Legacy {@code id|name|email|role}: no password, so always INACTIVE and unable to sign in. */
    private static User parseLegacy(String[] f) {
        String id = f[0].trim();
        String name = f[1];
        String email = f[2];
        UserRole role = UserRole.valueOf(f[3].trim());
        LocalDate migrated = LocalDate.now();
        return role == UserRole.ADMIN
                ? new AdminUser(id, name, email, "", "", User.NO_PASSWORD, AccountStatus.INACTIVE, migrated)
                : new RegularCustomer(id, name, email, "", "", User.NO_PASSWORD, AccountStatus.INACTIVE, migrated);
    }

    private void write(Snapshot snapshot, List<User> users) {
        List<String> lines = new ArrayList<>(snapshot.keptComments());
        lines.add(HEADER);
        users.stream()
                .sorted(Comparator.comparingInt(u -> idNumber(u.getId())))
                .map(FileUserRepository::serialise)
                .forEach(lines::add);
        if (!snapshot.unreadableLines().isEmpty()) {
            lines.add(UNREADABLE_NOTE);
            lines.addAll(snapshot.unreadableLines());
        }
        try {
            if (snapshot.legacyFound()) backUpLegacyFileOnce(snapshot.originalLines());
            storage.writeLinesAtomically(FILE_NAME, lines);
        } catch (IOException e) {
            throw new UserStorageException("Could not save account data.", e);
        }
    }

    private void backUpLegacyFileOnce(List<String> originalLines) throws IOException {
        try {
            storage.readLines(BACKUP_FILE_NAME);
            return; // already backed up
        } catch (NoSuchFileException e) {
            // continue
        }
        storage.writeLinesAtomically(BACKUP_FILE_NAME, originalLines);
        log.info("Legacy users.txt backed up to {} before converting it to the new format.", BACKUP_FILE_NAME);
    }

    static String serialise(User u) {
        String[] fields = {
                u.getId(), u.getRole().name(), u.getCustomerType().name(), u.getFullName(), u.getEmail(),
                u.getPhone(), u.getAddress(), u.getPasswordHash(), u.getStatus().name(),
                u.getCreatedDate().toString()
        };
        for (String field : fields) {
            if (UserInputRules.containsUnsafeCharacters(field)) {
                // Defence in depth: validation should already have stopped this.
                throw new IllegalStateException("Refusing to save a field containing | or a line break.");
            }
        }
        return String.join("|", fields);
    }

    private static void requireUniqueEmail(User candidate, List<User> users) {
        boolean taken = users.stream()
                .anyMatch(u -> u.getEmail().equals(candidate.getEmail())
                        && (candidate.getId() == null || !u.getId().equals(candidate.getId())));
        if (taken) throw new DuplicateEmailException();
    }

    /** Next ID after the highest ever seen, including unreadable lines, so IDs are never reused. */
    private static String nextId(Snapshot snapshot) {
        int max = snapshot.users().stream().mapToInt(u -> idNumber(u.getId())).max().orElse(0);
        for (String line : snapshot.unreadableLines()) {
            Matcher m = ID_NUMBER.matcher(line.split("\\|", 2)[0].trim());
            if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return String.format("U%03d", max + 1);
    }

    private static int idNumber(String id) {
        Matcher m = ID_NUMBER.matcher(id);
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static int indexOf(List<User> users, String id) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getId().equals(id)) return i;
        }
        return -1;
    }
}
