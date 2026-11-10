package com.bakery.user.repository;

import com.bakery.common.TextFileStorage;
import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.AdminUser;
import com.bakery.user.model.LoyaltyCustomer;
import com.bakery.user.model.RegularCustomer;
import com.bakery.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** File persistence for users.txt, run against a temporary directory (never the real data folder). */
class UserRepositoryTests {

    @TempDir
    Path dir;

    private TextFileStorage storage;
    private UserRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost: tests only

    @BeforeEach
    void setUp() {
        storage = new TextFileStorage(dir.toString());
        repository = new UserRepository(storage);
    }

    private User customer(String name, String email) {
        return new RegularCustomer(null, name, email, "077 123 4567", "12 Lake Road, Kandy",
                encoder.encode("Bakery2026"), AccountStatus.ACTIVE, LocalDate.of(2026, 11, 10));
    }

    @Test
    void savesAndReloadsWithSequentialIds() {
        assertEquals("U001", repository.save(customer("Nimal Perera", "nimal@example.com")).getId());
        assertEquals("U002", repository.save(customer("Kamala Silva", "kamala@example.com")).getId());

        UserRepository reopened = new UserRepository(storage);
        List<User> users = reopened.findAll();
        assertEquals(2, users.size());
        User nimal = reopened.findById("U001").orElseThrow();
        assertEquals("Nimal Perera", nimal.getFullName());
        assertEquals("12 Lake Road, Kandy", nimal.getAddress());
        assertEquals(LocalDate.of(2026, 11, 10), nimal.getCreatedDate());
    }

    @Test
    void storesBcryptHashNeverThePassword() throws IOException {
        repository.save(customer("Nimal Perera", "nimal@example.com"));
        String file = String.join("\n", storage.readLines(UserRepository.FILE_NAME));
        assertFalse(file.contains("Bakery2026"), "plain password must never be written");
        User reloaded = new UserRepository(storage).findByEmail("NIMAL@example.com").orElseThrow();
        assertTrue(reloaded.getPasswordHash().startsWith("$2a$"));
        assertTrue(encoder.matches("Bakery2026", reloaded.getPasswordHash()));
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively() {
        repository.save(customer("Nimal Perera", "nimal@example.com"));
        assertThrows(DuplicateEmailException.class, () -> repository.save(customer("Other Person", "Nimal@Example.com")));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void updateCannotTakeAnotherAccountsEmail() {
        repository.save(customer("Nimal Perera", "nimal@example.com"));
        repository.save(customer("Kamala Silva", "kamala@example.com"));
        assertThrows(DuplicateEmailException.class, () -> repository.update("U002", (u, all) -> {
            u.changeEmail("nimal@example.com");
            return u;
        }));
        assertEquals("kamala@example.com", repository.findById("U002").orElseThrow().getEmail());
    }

    @Test
    void readsEachSubclassBack() {
        repository.save(customer("Nimal Perera", "nimal@example.com"));
        repository.save(new AdminUser(null, "Bakery Admin", "admin@example.com", "", "",
                encoder.encode("Admin2026"), AccountStatus.ACTIVE, LocalDate.of(2026, 11, 10)));
        repository.update("U001", (u, all) -> new LoyaltyCustomer(u.getId(), u.getFullName(), u.getEmail(),
                u.getPhone(), u.getAddress(), u.getPasswordHash(), u.getStatus(), u.getCreatedDate()));

        UserRepository reopened = new UserRepository(storage);
        assertInstanceOf(LoyaltyCustomer.class, reopened.findById("U001").orElseThrow());
        assertInstanceOf(AdminUser.class, reopened.findById("U002").orElseThrow());
    }

    @Test
    void keepsMalformedLinesAndNeverReusesTheirIds() throws IOException {
        storage.writeLines(UserRepository.FILE_NAME, List.of(
                UserRepository.HEADER,
                "U007|CUSTOMER|REGULAR|Broken|not-an-email|||-|ACTIVE|2026-11-10",
                "garbage without delimiters"));
        assertTrue(repository.findAll().isEmpty());

        User saved = repository.save(customer("Nimal Perera", "nimal@example.com"));
        assertEquals("U008", saved.getId(), "ID of an unreadable record must not be reused");
        List<String> lines = storage.readLines(UserRepository.FILE_NAME);
        assertTrue(lines.contains("U007|CUSTOMER|REGULAR|Broken|not-an-email|||-|ACTIVE|2026-11-10"));
        assertTrue(lines.contains("garbage without delimiters"));
    }

    @Test
    void migratesLegacyFormatSafelyWithBackup() throws IOException {
        List<String> legacy = List.of(
                "# User Management - fictional sample records only",
                "# id|name|email|role",
                "U001|Sample Customer|customer@example.test|CUSTOMER");
        storage.writeLines(UserRepository.FILE_NAME, legacy);

        User migrated = repository.findById("U001").orElseThrow();
        assertEquals(AccountStatus.INACTIVE, migrated.getStatus(), "legacy records must not become working accounts");
        assertFalse(migrated.hasUsablePassword());

        repository.save(customer("Nimal Perera", "nimal@example.com"));
        assertEquals(legacy, storage.readLines(UserRepository.BACKUP_FILE_NAME));
        List<String> lines = storage.readLines(UserRepository.FILE_NAME);
        assertEquals("# User Management - fictional sample records only", lines.get(0));
        assertEquals(UserRepository.HEADER, lines.get(1));
        assertTrue(lines.get(2).startsWith("U001|CUSTOMER|REGULAR|Sample Customer|customer@example.test|||-|INACTIVE|"));
        assertTrue(lines.get(3).startsWith("U002|"));
    }

    @Test
    void deletedAccountsKeepTheirIdSoItIsNeverReused() {
        repository.save(customer("Nimal Perera", "nimal@example.com"));
        repository.update("U001", (u, all) -> {
            u.markDeleted();
            return u;
        });
        assertEquals("U002", repository.save(customer("Nimal Again", "nimal@example.com")).getId());
    }

    @Test
    void serialiserRefusesDelimiterInStoredFields() {
        // The model already blocks this; serialise() is a second line of defence.
        User user = customer("Nimal Perera", "nimal@example.com");
        user.assignId("U001");
        assertEquals(10, UserRepository.serialise(user).split("\\|", -1).length);
    }
}
