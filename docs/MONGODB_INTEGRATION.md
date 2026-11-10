# MongoDB integration (User Management + Order Management)

User and Order Management can store their data in **MongoDB** (default) or in the original
**TXT files**. Controllers, services, security, routes and templates are the same in both modes;
only the repository implementation changes. The other team modules are not affected.

## Architecture

```
Controller ──> Service ──> UserRepository / OrderRepository  (interfaces, unchanged contracts)
                                   │
              ┌────────────────────┴─────────────────────┐
      bakery.storage.type=file                 bakery.storage.type=mongodb
   FileUserRepository (users.txt)          MongoUserRepository  ──> UserDocumentRepository ─┐
   FileOrderRepository (orders.txt)        MongoOrderRepository ──> OrderDocumentRepository ─┤
                                           MongoSequenceGenerator (counters)                 │
                                                                                   MongoDB bakery_db
```

| Piece | Where |
| --- | --- |
| Repository contracts | `user/repository/UserRepository`, `order/repository/OrderRepository` |
| TXT implementations (unchanged behaviour) | `FileUserRepository`, `FileOrderRepository` |
| MongoDB implementations | `user/repository/mongo/*`, `order/repository/mongo/*` |
| Shared subclass mapping (used by both stores) | `UserFactory`, `OrderFactory` |
| Mode selection, ID counters, collection set-up | `common/persistence/*` |
| Optional TXT → MongoDB migration | `common/persistence/migration/TxtToMongoMigration` |

**Domain classes are not changed.** `User`, `RegularCustomer`, `LoyaltyCustomer`, `AdminUser`, `Order`,
`PickupOrder`, `DeliveryOrder` keep their validating constructors and have no persistence
annotations. Separate document classes (`UserDocument`, `OrderDocument`) are mapped to and from them.

**Inheritance strategy:** one collection per hierarchy with explicit discriminators:
`users.role` + `users.customerType` (→ Regular/Loyalty/Admin) and `orders.orderType` (→ Pickup/Delivery).
`UserFactory` / `OrderFactory` rebuild the right subclass, so discounts and `calculateTotal()` stay
polymorphic. Totals are never stored; they are always calculated.

## Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```
Version managed by Spring Boot 3.5.16. No JPA, Hibernate or SQL database.

## Configuration

`src/main/resources/application.properties`:

```properties
bakery.storage.type=${BAKERY_STORAGE:mongodb}
spring.data.mongodb.uri=${MONGODB_URI:mongodb://127.0.0.1:27017/bakery_db}
spring.data.mongodb.auto-index-creation=false   # indexes are created explicitly (below)
```

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `BAKERY_STORAGE` | `mongodb` | `mongodb` or `file`. Anything else stops startup with a clear message. |
| `MONGODB_URI` | `mongodb://127.0.0.1:27017/bakery_db` | Connection string. Put credentials here (environment only), never in Git. |
| `BAKERY_DATA_DIRECTORY` | `data` | Folder for TXT mode (use `data-local`, which Git ignores). |

The local default has **no authentication** and must only be used on localhost. The startup log
shows the active store and host/database (never user names or passwords). Connection and server
selection time out after 5 seconds so a stopped MongoDB service is reported quickly.

## Local MongoDB setup

MongoDB Community Server 8.2 as a Windows service, `mongosh` 2.5:

```powershell
Get-Service MongoDB                     # Status should be Running
mongosh "mongodb://127.0.0.1:27017/bakery_db" --eval "db.getCollectionNames()"
```

Database `bakery_db`, collections:

| Collection | Contents |
| --- | --- |
| `users` | accounts (`_id` = U001…, role, customerType, fullName, email, phone, address, passwordHash, status, createdDate, version) |
| `orders` | orders (`_id` = O001…, customerId, productId, productName, quantity, unitPrice, orderType, deliveryAddress, status, orderDate, version) |
| `counters` | ID sequences: `{ _id: "users", seq: N }`, `{ _id: "orders", seq: N }` |

Collections, validators, indexes and counters are created automatically (idempotently) the first
time the application starts in MongoDB mode. Nothing is ever dropped or deleted at startup.

## Storage mode selection

```powershell
# MongoDB (default)
.\mvnw.cmd spring-boot:run

# TXT files (original implementation)
$env:BAKERY_STORAGE = 'file'; $env:BAKERY_DATA_DIRECTORY = 'data-local'
.\mvnw.cmd spring-boot:run
Remove-Item Env:BAKERY_STORAGE
```

Exactly one `UserRepository` and one `OrderRepository` bean exist (`@ConditionalOnProperty` on
`bakery.storage.type`). Switching mode does **not** copy data; use the migration below.

## Indexes, validation and IDs

| Collection | Index | Purpose |
| --- | --- | --- |
| users | `_id` | account ID |
| users | `uk_users_email` (unique) | one account per normalised email, also under concurrent registrations |
| users | `idx_users_role_status` | admin customer list / counts |
| orders | `_id` | order ID |
| orders | `idx_orders_customer_date` (customerId, orderDate desc) | My Orders |
| orders | `idx_orders_status` | admin status filters / counts |
| orders | `idx_orders_date` | newest-first sorting |

* **Schema validators** (`$jsonSchema`, strict): required fields, ID formats (`^U[0-9]{3,}$`,
  `^O[0-9]{3,}$`), enum values for role/type/status, quantity ≥ 1, unit price > 0, ISO dates.
  MongoDB rejects malformed documents even if written outside the application.
* **Application validation** is unchanged (UserService / OrderService) and runs in both modes;
  the document mappers refuse missing values and `|` / line breaks as a second check.
* A stored document that breaks a domain rule is skipped on read and logged **by ID only**
  (no personal data, no hashes).
* **IDs** come from an atomic `findAndModify $inc` on `counters`: concurrent requests never receive
  the same ID, IDs survive restarts and are never reused (deleted accounts keep theirs). If a
  record with the next ID already exists (e.g. inserted by hand), the counter is moved past the
  highest existing ID (`$max`, never backwards) and the save is retried.
* **Concurrent edits** use optimistic locking (`version` field). A conflicting update is re-applied to
  the latest copy with a short randomised back-off (up to 10 attempts), and business rules such as
  "only pending orders can be edited" are re-checked each time.
* **Errors:** database failures become `UserStorageException` / an `IOException` that OrderService turns
  into a friendly message. Driver details are only logged (class name), never shown to users.

Security is unchanged: BCrypt hashes, roles, ownership checks (`OrderService`), admin-only routes,
CSRF. Password hashes never leave the repository layer and are never logged.

## Running the tests

```powershell
.\mvnw.cmd clean verify
```

* Existing suites (TXT mode, temporary folders) run as before: `src/test/resources/config/application.properties`
  sets `bakery.storage.type=file` and points any accidental MongoDB use at `bakery_test_unused`.
* MongoDB suites (`com.bakery.mongo.*`) use the local server with a **random `bakery_test_xxxxxxxx`
  database**. Each test empties only that database's `users`, `orders` and `counters`; the database is
  dropped when the test JVM exits. The helper refuses to touch any database whose name does not start
  with `bakery_test_`. **`bakery_db` is never used by tests.**
* If MongoDB is not running, the MongoDB tests are **skipped** (not failed), so teammates without
  MongoDB still get a green build.

## Checking documents with mongosh

```powershell
mongosh "mongodb://127.0.0.1:27017/bakery_db"
```
```js
db.users.find({}, { passwordHash: 0 })                 // accounts without hashes
db.users.findOne({ email: "test.customer@example.com" }, { passwordHash: 0 })
db.orders.find({ customerId: "U002" }).sort({ orderDate: -1 })
db.orders.countDocuments({ status: "PENDING" })
db.counters.find()
db.users.getIndexes(); db.orders.getIndexes()
db.getCollectionInfos({ name: "orders" })[0].options.validator
```

## TXT → MongoDB migration (optional, explicit)

Nothing is imported automatically. The migration only runs when `bakery.migration.mode` is set,
reads the TXT files **without changing them**, and only **inserts** (never updates or deletes).

Rules:
* Unreadable lines (bad format, duplicate ID or email inside the file) are counted and not imported.
* Same ID with a different email, or same email under a different ID, already in MongoDB → **conflict**,
  reported and skipped. Existing MongoDB records are never overwritten. Re-running is safe (idempotent).
* Accounts without a usable password (e.g. the scaffold sample) are imported only as **INACTIVE**
  accounts that cannot sign in.
* Orders are imported only if their customer is a CUSTOMER in the **same** source `users.txt` that is
  (or already was) migrated with the same identity. Orphan orders, admin-owned orders, and orders
  **dated before the customer's account was created** (typical of sample data such as the tracked
  orders for U001-U003) are skipped, so sample orders can never appear in a real customer's My Orders.
* Emails are masked in the report (`n***@example.com`); hashes are never printed.

**1. Back up first**

```powershell
mongodump --db bakery_db --out "backup\bakery_db_$(Get-Date -Format yyyyMMdd_HHmmss)"
Copy-Item data-local "backup\data-local_$(Get-Date -Format yyyyMMdd_HHmmss)" -Recurse
```
(`mongodump` is part of the MongoDB Database Tools; install them if the command is missing.)

**2. Dry run (writes nothing)**

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --bakery.migration.mode=dry-run --bakery.migration.source=data-local"
```

Read the report: each record shows IMPORT, SKIPPED or CONFLICT with the reason.

**3. Apply**

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.main.web-application-type=none --bakery.migration.mode=apply --bakery.migration.source=data-local"
```

The application exits after the report (no web server). Counters are moved past the imported IDs.

**Rollback:** restore the backup (`mongorestore --drop --nsInclude "bakery_db.*" <backup-folder>`), or delete only
the IDs listed as IMPORT in the report, e.g. `db.users.deleteMany({ _id: { $in: ["U002","U003"] } })`.
The TXT files are never modified, so TXT mode keeps working throughout.

Note: the tracked sample `data/orders.txt` refers to customers U001-U003 that do not exist in the
tracked `data/users.txt`; do not use `data/` as a migration source for real accounts.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Startup fails after ~5 s with `MongoTimeoutException` / "Timed out while waiting for a server" | Start the service: `Start-Service MongoDB`, or run with `BAKERY_STORAGE=file`. |
| `Invalid bakery.storage.type '…'` | Use `mongodb` or `file`. |
| "We couldn't reach the account records just now" on a page | MongoDB stopped while the app was running; restart the service. |
| Registration says the email already exists | Unique email index; check `db.users.findOne({email: "…"}, {passwordHash: 0})`. |
| `Document failed validation` when inserting by hand in mongosh | The schema validator protects the collections; insert through the application or match the schema. |
| "Could not apply the schema validator" warning | The MongoDB user lacks `collMod` rights (e.g. hosted cluster). The app still validates every record. |
| No admin in MongoDB | Run the admin bootstrap once (docs/USER_MANAGEMENT.md) or migrate your local admin from `data-local`. |

## Limitations and future integration

* A standalone local MongoDB has no multi-document transactions; each write is atomic per document,
  and cross-record rules (e.g. "last active admin") are re-checked on the latest data but two admins
  removing themselves at the exact same moment is not fully serialised.
* Product, Payment, Custom Cake and Feedback modules still use their own TXT files; nothing here
  changes them. When Product Management moves to MongoDB, OrderService should look up the unit price
  by `productId` instead of accepting it from the order form.
* TXT mode keeps its single-instance limitation (file lock per JVM). MongoDB mode is safe for
  concurrent requests within one instance; running several instances also works for IDs and emails.
* Loyalty discounts are still not applied to orders.
