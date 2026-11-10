package com.bakery.common.persistence.migration;

import com.bakery.common.TextFileStorage;
import com.bakery.order.model.Order;
import com.bakery.order.repository.FileOrderRepository;
import com.bakery.order.repository.mongo.MongoOrderRepository;
import com.bakery.user.model.User;
import com.bakery.user.model.UserRole;
import com.bakery.user.repository.FileUserRepository;
import com.bakery.user.repository.mongo.MongoUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * <b>Explicit, opt-in</b> one-time copy of users.txt and orders.txt into MongoDB. Never runs unless
 * {@code bakery.migration.mode} is {@code dry-run} or {@code apply} (and storage is {@code mongodb}).
 *
 * <ul>
 *   <li>The TXT files are only read, never changed.</li>
 *   <li>Records are only <b>inserted</b>; an existing MongoDB record is never overwritten. Running it
 *       again skips what is already there (idempotent).</li>
 *   <li>Same ID with a different email, or same email under a different ID, is a <b>conflict</b>:
 *       reported and skipped.</li>
 *   <li>Accounts without a usable password (e.g. the scaffold sample) are imported only as
 *       INACTIVE accounts that cannot sign in.</li>
 *   <li>Orders are imported only if their customer exists in the <i>same</i> source users.txt as a
 *       CUSTOMER and is (or already was) migrated with the same identity. Orders dated before the
 *       customer's account was created are skipped as likely sample data, so old sample orders such as
 *       those for U001-U003 cannot appear in a real customer's My Orders.</li>
 * </ul>
 * See docs/MONGODB_INTEGRATION.md for commands, backup and rollback.
 */
@Component
@ConditionalOnExpression("'${bakery.migration.mode:off}' != 'off' and '${bakery.storage.type:file}'.equalsIgnoreCase('mongodb')")
public class TxtToMongoMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TxtToMongoMigration.class);

    private final MongoUserRepository mongoUsers;
    private final MongoOrderRepository mongoOrders;
    private final String mode;
    private final String source;

    public TxtToMongoMigration(MongoUserRepository mongoUsers, MongoOrderRepository mongoOrders,
                               @Value("${bakery.migration.mode}") String mode,
                               @Value("${bakery.migration.source:data-local}") String source) {
        this.mongoUsers = mongoUsers;
        this.mongoOrders = mongoOrders;
        this.mode = mode.trim().toLowerCase(Locale.ROOT);
        this.source = source;
    }

    /** What happened to one record. */
    public record Line(String kind, String id, String outcome, String detail) {
    }

    /** Full result, also returned for tests. */
    public record Report(boolean applied, int sourceUsers, int sourceOrders, int unreadableUserLines,
                         int unreadableOrderLines, List<Line> lines) {
        public long count(String kind, String outcome) {
            return lines.stream().filter(l -> l.kind().equals(kind) && l.outcome().equals(outcome)).count();
        }
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!mode.equals("dry-run") && !mode.equals("apply")) {
            log.error("Migration not run: bakery.migration.mode must be 'dry-run' or 'apply' (was '{}').", mode);
            return;
        }
        Report report = migrate(Path.of(source), mode.equals("apply"));
        print(report);
    }

    /** Reads {@code sourceDir}, validates, and (if {@code apply}) inserts the importable records. */
    public Report migrate(Path sourceDir, boolean apply) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("Source folder not found: " + sourceDir.toAbsolutePath());
        }
        TextFileStorage storage = new TextFileStorage(sourceDir.toString());
        FileUserRepository.ReadReport users = new FileUserRepository(storage).readForMigration();
        FileOrderRepository.ReadReport orders = new FileOrderRepository(storage).readForMigration();
        List<Line> lines = new ArrayList<>();

        // ── Users ──
        Map<String, User> importableCustomers = new HashMap<>();   // id → user that will exist in MongoDB
        List<User> toInsert = new ArrayList<>();
        for (User u : users.users()) {
            Optional<User> byId = mongoUsers.findById(u.getId());
            Optional<User> byEmail = mongoUsers.findByEmail(u.getEmail());
            if (byId.isPresent()) {
                if (byId.get().getEmail().equals(u.getEmail())) {
                    lines.add(new Line("user", u.getId(), "skipped", "already in MongoDB"));
                    if (u.getRole() == UserRole.CUSTOMER) importableCustomers.put(u.getId(), u);
                } else {
                    lines.add(new Line("user", u.getId(), "conflict", "ID already used in MongoDB by another email"));
                }
            } else if (byEmail.isPresent()) {
                lines.add(new Line("user", u.getId(), "conflict",
                        "email " + mask(u.getEmail()) + " already used in MongoDB by " + byEmail.get().getId()));
            } else {
                String note = u.hasUsablePassword() ? u.getRole() + ", " + u.getStatus()
                        : "no password: imported as INACTIVE, cannot sign in";
                lines.add(new Line("user", u.getId(), "import", mask(u.getEmail()) + " (" + note + ")"));
                toInsert.add(u);
                if (u.getRole() == UserRole.CUSTOMER) importableCustomers.put(u.getId(), u);
            }
        }

        // ── Orders ──
        List<Order> ordersToInsert = new ArrayList<>();
        for (Order o : orders.orders()) {
            User customer = importableCustomers.get(o.getCustomerId());
            if (customer == null) {
                lines.add(new Line("order", o.getId(), "skipped",
                        "customer " + o.getCustomerId() + " is not a migrated customer in this source (orphan or sample order)"));
                continue;
            }
            if (o.getOrderDate().isBefore(customer.getCreatedDate())) {
                lines.add(new Line("order", o.getId(), "skipped",
                        "dated before customer " + customer.getId() + "'s account was created (likely sample data)"));
                continue;
            }
            Optional<Order> existing = mongoOrders.findById(o.getId());
            if (existing.isPresent()) {
                boolean same = existing.get().getCustomerId().equals(o.getCustomerId());
                lines.add(new Line("order", o.getId(), same ? "skipped" : "conflict",
                        same ? "already in MongoDB" : "ID already used in MongoDB by another customer"));
                continue;
            }
            lines.add(new Line("order", o.getId(), "import", "customer " + o.getCustomerId() + ", " + o.getStatus()));
            ordersToInsert.add(o);
        }

        if (apply) {
            for (User u : toInsert) {
                if (!mongoUsers.importUser(u)) replace(lines, "user", u.getId(), "conflict", "inserted concurrently; not overwritten");
            }
            for (Order o : ordersToInsert) {
                if (!mongoOrders.importOrder(o)) replace(lines, "order", o.getId(), "conflict", "inserted concurrently; not overwritten");
            }
            mongoUsers.syncSequence();
            mongoOrders.syncSequence();
        }
        return new Report(apply, users.users().size(), orders.orders().size(), users.unreadableLines(),
                orders.unreadableLines(), List.copyOf(lines));
    }

    private static void replace(List<Line> lines, String kind, String id, String outcome, String detail) {
        lines.replaceAll(l -> l.kind().equals(kind) && l.id().equals(id) ? new Line(kind, id, outcome, detail) : l);
    }

    private void print(Report r) {
        log.info("==== TXT -> MongoDB migration ({}) from '{}' ====", r.applied() ? "APPLY" : "DRY RUN", source);
        log.info("Source: {} readable users ({} unreadable lines), {} readable orders ({} unreadable lines)",
                r.sourceUsers(), r.unreadableUserLines(), r.sourceOrders(), r.unreadableOrderLines());
        for (Line l : r.lines()) {
            log.info("  {} {} -> {}: {}", l.kind(), l.id(), l.outcome().toUpperCase(Locale.ROOT), l.detail());
        }
        log.info("Users:  {} to import, {} skipped, {} conflicts", r.count("user", "import"), r.count("user", "skipped"), r.count("user", "conflict"));
        log.info("Orders: {} to import, {} skipped, {} conflicts", r.count("order", "import"), r.count("order", "skipped"), r.count("order", "conflict"));
        log.info(r.applied() ? "Done. Records marked IMPORT were inserted; nothing was overwritten."
                             : "Dry run only: nothing was written. Re-run with bakery.migration.mode=apply to import.");
    }

    /** n***@example.com: enough to recognise an account, without printing full addresses. */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
