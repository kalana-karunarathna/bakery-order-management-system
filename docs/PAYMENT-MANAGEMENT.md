# Dasun — Payment & Invoice Management

## Repository findings
The local checkout uses Java 21, Spring Boot 3.5.16, Maven, Thymeleaf and local
Bootstrap 5.3.8. There is no React application. Packages follow
controller -> service -> repository -> model under com.bakery.
The shared layout is templates/fragments/layout.html; the brown/cream theme is
static/css/style.css. Every other module currently has only a landing controller,
a placeholder page and package documentation. There is no login, session identity,
admin authorization service, Order model/service or CakeBooking model/service.

Existing data samples are fictional. orders.txt has product ID and quantity but
no agreed order total. cake-bookings.txt has no quoted price. users.txt has no
password or authentication contract. Payment code therefore does not interpret
these files as authoritative payable transactions or create another member's services.

## Implemented routes
- GET /payments: payment history, exact ID search, method/status filters.
  Anonymous users see an integration notice and no records.
- GET /payments/new and POST /payments: record a pending payment.
- GET /payments/{id}: payment details and printable invoice.
- POST /payments/{id}/edit: edit amount/reference of a pending record.
- POST /payments/{id}/status: validate and apply a status transition.
- GET /payments/admin: administrator history dashboard.
- POST /payments/{id}/delete: administrator deletion with explicit confirmation.

The four screens use the existing shared header, footer, theme and Bootstrap
responsive grid/table. All mutations are POST with session CSRF tokens.

## Integration contracts (required before normal browser use)
PaymentIntegration.CustomerDirectory can optionally be supplied as a Spring bean to verify that customer IDs still exist. Without it, identity/ownership relies on the trusted IdentityProvider and PayableProvider contracts below.

PaymentIntegration.IdentityProvider must be supplied as a Spring bean by
Matheesha's authentication integration. Its current(HttpSession) returns
Actor(customerId, admin) only after trusted server-side authentication; return
null otherwise. Do not create this Actor from query parameters, posted IDs or
a role selected by the visitor. Rotate the session on login and invalidate it
on logout. Map the actual User Management role to admin.

PaymentIntegration.PayableProvider must be supplied as ONE Spring bean combining
Kalana's order service and Salila's cake service. availableTo(Actor) returns
Payable(TargetType.ORDER or BOOKING, actualId, actualCustomerId, agreedTotal).
Use actual service objects and their persisted agreed totals. Exclude cancelled
or otherwise nonpayable orders/bookings. An admin may receive all payable
targets; customers receive only their own. PaymentService checks ownership again.

Example shape once those services exist:
  @Bean
  PayableProvider paymentTargets(OrderService orders, CakeBookingService cakes) {
      return actor -> /* map real order and booking objects to Payable records */;
  }
This is an illustrative contract, not a declaration of existing service APIs.
There must be one combined provider rather than two competing beans.

Boot auto-configuration supplies fail-closed defaults only when these beans
are absent: anonymous identity and an empty payable list. There is deliberately
no public demo login, selectable admin role or invented cake total.
PaymentPageTests supplies TEST-ONLY trusted identities and payable targets and
exercises all pages end to end. These fixtures are not production authentication.
The payment module compiles and runs while integration is pending.

Agreed totals are captured in each payment for invoice stability. The owning
modules should freeze totals after payment starts, or coordinate price changes
with the payment module. Pending payments reserve balance. A FULL payment must
equal the available remaining balance; PARTIAL permits a positive amount up to that balance; ADVANCE is booking-only and strictly less
than that balance. Invoices show total less PAID records (pending records are
not receipts; REFUNDED records no longer reduce the balance).

## Persistence
payments.txt is UTF-8, pipe-delimited, with comment headers.
New format:
v1|id|targetType|targetId|customerId|total|amount|method|purpose|status|reference|requestKey|createdAt

New IDs are sequential PAY001, PAY002, etc. (older UUID records remain readable); invoice IDs are INV- followed by the payment ID.
Fields reject pipes/newlines; currency uses BigDecimal with exactly two decimal
places. Only receipt/transaction reference labels are stored, never card data.
The original exact PAY001 scaffold example is preserved but excluded from live
history because it has no authenticated ownership or total snapshot.
Other unrecognized records fail closed instead of silently disappearing.

PaymentRepository uses its own atomic replacement because the existing shared
TextFileStorage explicitly lacks atomic writes. No shared utility was modified.
A synchronized read/validate/write boundary serializes payment operations inside
one application instance; failed writes report a storage error. Temporary files
are cleaned. The filesystem must support atomic move. Run ONE application
instance per data directory. There is no cross-process file lock or database.

## Status rules and security
PENDING -> CANCELLED: owner or admin.
PENDING -> PAID or FAILED: admin only.
PAID -> REFUNDED: admin only, after independently completing the manual refund.
FAILED, CANCELLED and REFUNDED are terminal.
Only FAILED/CANCELLED records may be deleted, by admins with confirmation.
Editing is pending-only. Payment method, target and customer are immutable.
Duplicate form submissions and duplicate noncash transaction references are
rejected. Confirmed and pending totals cannot overpay a target.

Every service read/edit/status operation checks ownership; customers receive 404
for someone else's ID. Admin routes independently enforce admin role.
The browser is not trusted for amount totals, owner identity or role.
This is an educational manual recording system, with no gateway or simulated
bank verification. Only admins confirm independently verified funds.

## IntelliJ IDEA run instructions
1. Open this existing folder containing pom.xml as a Maven project.
2. Install/select JDK 21 in File > Project Structure > Project SDK.
3. Under Settings > Build Tools > Maven, use Maven Wrapper; set importer JDK and
   runner JRE to Project SDK 21. Reload Maven dependencies.
4. In the terminal, set JAVA_HOME to the same JDK 21, then run:
   .\mvnw.cmd clean verify
5. Run com.bakery.BakeryApplication. Set the working directory to the repository
   root. Visit http://localhost:8080/payments.
6. For isolated runtime data, set BAKERY_DATA_DIRECTORY=data-local in the run
   configuration environment. New payments.txt is created on the first write.
7. Until the integration beans above exist, browser access intentionally shows
   no payments and creation/admin routes reject anonymous access. Run
   PaymentPageTests in IntelliJ to exercise all screens with trusted test fixtures.
8. After integrating User/Order/Cake services, log in through User Management,
   create an actual order or quoted booking, record a payment, then use an admin
   account to verify receipt and confirm it. Print from Invoice Details.
9. Stop and restart the application with the SAME working/data directory; the
   payment remains. Use a second customer to check that the invoice returns 404.
10. If port 8080 is busy use program argument --server.port=8081.

## Viva in simple English
CREATE: I retrieve the real payable record through an integration interface,
check its owner and balance, generate an ID and save a Pending payment.
READ: I show only the customer's records, with search, filters and an invoice.
UPDATE: I allow pending amount/reference edits and validate status transitions.
Only an administrator can confirm money received or record a refund.
DELETE: An administrator confirms deletion; only Failed/Cancelled records qualify.

Encapsulation: Payment keeps fields private. Getters expose values; setters and
transition methods reject invalid changes. IDs and ownership cannot be changed.
Abstraction: Payment is abstract, and integration interfaces hide other modules.
Inheritance: CashPayment, CardPayment and OnlineTransfer extend Payment.
Polymorphism: processPayment() is overridden in each subclass. The service calls
it through the Payment type; each method describes its own manual recording flow.
Exception handling: invalid operations become clear 400 responses; unavailable
storage becomes 503; unauthorized access is rejected on the server.
BigDecimal avoids floating-point rounding errors in totals and remaining balance.
Controller handles HTTP, service applies rules, repository handles serialization.

## Automated verification
PaymentTests covers creation, restart through a fresh repository instance,
history filters, status rules, deletion, invalid amounts, duplicate submissions,
advance deposits, remaining balances, ownership, corrupted-file preservation,
concurrent overpayment prevention and controller CSRF/admin checks.
PaymentPageTests renders all four pages with real Thymeleaf, submits a payment
and status update, and checks invoice access using trusted TEST-ONLY adapters.
Existing home/module page and shared storage tests remain unchanged.
Persistence testing reopens the file through a new service/repository; it is not
a full separate-process restart. Follow run instruction 9 for that manual check.
Responsive Bootstrap and print CSS are implemented; no visual browser automation
is configured in this repository.

## Changed files
Modified:
- src/main/java/com/bakery/payment/controller/PaymentController.java
- src/main/resources/templates/payment/index.html

Created:
- src/main/java/com/bakery/payment/model/Payment.java
- src/main/java/com/bakery/payment/model/CardPayment.java
- src/main/java/com/bakery/payment/model/CashPayment.java
- src/main/java/com/bakery/payment/model/OnlineTransfer.java
- src/main/java/com/bakery/payment/repository/PaymentRepository.java
- src/main/java/com/bakery/payment/service/PaymentIntegration.java
- src/main/java/com/bakery/payment/service/PaymentIntegrationConfiguration.java
- src/main/java/com/bakery/payment/service/PaymentService.java
- src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
- src/main/resources/templates/payment/make.html
- src/main/resources/templates/payment/admin.html
- src/main/resources/templates/payment/invoice.html
- src/main/resources/templates/payment/fragments.html
- src/main/resources/templates/payment/error.html
- src/test/java/com/bakery/payment/PaymentTests.java
- src/test/java/com/bakery/payment/PaymentPageTests.java
- docs/PAYMENT-MANAGEMENT.md

No other members' modules, shared layout, dependencies or tracked sample data
were changed. Work is on feature/payment-management.

Suggested commit:
feat(payment): add manual payment CRUD, invoices, secure integration ports and tests

## Verification result (15 October 2026)
Maven verify completed successfully: 19 tests, zero failures/errors; executable
JAR packaged. The machine has JDK 26 and an older JAVA_HOME by default, so this
verification explicitly used JDK 26 with the unchanged Maven release target 21.
A JDK 21 runtime was not installed locally; use JDK 21 in IntelliJ as described.
Packaged JAR startup was checked on port 18081: /payments returned 200 and the
integration notice; /payments/new and /payments/admin returned 401 anonymously.
The temporary application process was stopped. Tracked sample files were
unchanged. Browser visual checks and a separate-process payment persistence
restart remain manual checks; automated persistence reopens the same temporary
file through fresh repository and service objects.
