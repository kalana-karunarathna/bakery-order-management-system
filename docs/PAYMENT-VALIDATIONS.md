# Payment validation guide — Dasun

All changes are in the Payment module, its templates, its tests and documentation.
The shared layout, other team members' modules and tracked data were not changed.

## What each validation does
| Area | Validation in simple English |
| --- | --- |
| Payment ID | The server creates PAY001, PAY002 and so on. The counter is stored in payments.txt and is not reset when a record is deleted. Existing UUID IDs stay readable. The fictional PAY001 sample reserves that ID, so a checkout containing that sample starts live records at PAY002. |
| Customer | The server uses the authenticated identity and the payable record's owner, never a posted customer ID. Empty identities are rejected. An optional CustomerDirectory bean checks current user existence when User Management provides it. |
| Order/booking | A trusted provider must return a real payable target owned by the customer. Cancelled targets must be excluded by that provider. Edits and confirmation recheck its owner and agreed total. Missing modules use the existing fail-closed defaults. |
| Amount | Required; numeric; greater than zero; at most two decimals; at most LKR 999,999,999,999.99; no NaN, Infinity, exponents or amounts above available balance. Java uses BigDecimal; browser validation compares integer cents. |
| Purpose | PARTIAL accepts any valid amount up to available balance. FULL must equal available balance. ADVANCE is only for a booking and must be less than the available balance. Pending payments reserve money to prevent concurrent overpayment. |
| Method | Only CASH, CARD and BANK_TRANSFER are accepted. Invalid/missing HTTP values return field errors. The service also rejects a null method. |
| Date | Created on the server using Instant.now. Posted dates are ignored. Future timestamps are rejected by the domain model, including when reading a damaged file. |
| Reference | Trimmed; 3-80 characters; letters, digits, spaces, dot, slash, underscore and hyphen. The first character must be a letter or digit. Unique across all methods, case insensitive. Cash can automatically use CASH-PAY001. |
| New status | Always PENDING, regardless of posted status. A pending record is not a verified bank or card transaction. |
| Status changes | Owner/admin may cancel a pending record. Only admins may mark Pending as Paid/Failed or Paid as Refunded. Closed states cannot be reopened. Invalid transitions show a clear error. |
| Update | Only pending amount/reference can change. IDs, owner, target and method remain immutable. Failed validation does not write the file. Entered values stay on the form. |
| Delete | Admin only; only Failed/Cancelled; checked confirmation and a JavaScript confirmation dialog. Paid/Refunded records are preserved. Missing IDs return 404 without revealing other customers' records. |
| Search | Trims the query, limits its length, validates enum filters and shows No payments found for unmatched IDs. |
| Invoice | Checks ownership before view/download. Uses the saved record, LKR formatting and a nonnegative remaining balance. Pending/Failed records are explicitly not paid receipts. Only PAID contributes to the displayed confirmed amount. |
| File | Missing/empty data is handled. Writes create the file. Duplicate IDs/keys/references, inconsistent target data, overpayments and malformed records fail closed. Atomic replacement and a shared per-path JVM lock protect writes. Existing bytes are retained when validation fails. |
| Feedback | Errors appear below fields and in a summary; invalid inputs have red borders and accessible labels. Messages clear when corrected. Success messages follow create/update/status/delete. |
| Security | All POST actions require a session CSRF token. Admin and ownership checks run in Java. Payment pages disable browser caching. Card numbers, CVVs and banking credentials are never requested or stored. |

References must stay unique among retained records, including failed/cancelled/
refunded records. Deleted records are removed, so their former transaction
references and submission keys are no longer present for duplicate checking.
The persistent ID counter still prevents deleted payment IDs being reused.

## Invoice download and printing
Invoice Details has Print invoice and Download invoice (HTML).
Print uses the browser dialog, including Save as PDF if supported by the browser.
The download is HTML, not a server-generated PDF. It is authorized through the
same payment service; the downloaded document contains no edit/status forms.
The inline invoice styles keep the downloaded information readable offline.

## Integration remains required
There is no production login, order service or booking service in the scaffold.
Supply IdentityProvider, PayableProvider and, when available, CustomerDirectory
beans using the real modules. A PayableProvider must exclude cancelled targets.
The current validation work does not enable a public demo identity/admin switch.
The integration notice on an anonymous browser is expected.

## Run and verify
From the repository root in PowerShell:
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-26'
    .\mvnw.cmd verify
    node --test src/test/js/payment-validation.test.cjs
    .\mvnw.cmd spring-boot:run

JDK 26 is installed on this computer; Maven still compiles with release target 21.
The repository's recommended IntelliJ SDK remains JDK 21.
Node is used only for the optional JavaScript tests. There is no npm application
or frontend dependency installation. Restart any existing app process after the
changes, then open http://localhost:8080/payments.

## Tests
The Java suite covers every requested category: missing/negative/zero/excessive
amounts; invalid decimals, NaN and Infinity; missing customer; invalid target;
missing/invalid method; duplicate reference; invalid status; unauthorized Paid
confirmation; editing/deleting Paid; deleting Cancelled; invalid invoice ID;
cross-customer access; valid create/update; missing file; restart persistence.
Extra checks cover ID counters after deletion, original sample preservation,
future dates, posted identity/date/status tampering, corruption, cancelled or
changed payable targets, success messages and LKR invoice downloads.
Real Thymeleaf tests render invalid create/edit forms with preserved values.
Dependency-free Node tests execute the production validation script against form
event fixtures. They check submission blocking, red borders, correction, server
duplicate messages, dropdowns, balances and the deletion dialog. These are DOM
fixtures, not a full visual browser test.
Persistence tests reopen the file using a fresh repository/service; no separate
process payment restart or live bank/payment gateway is simulated.

## Files changed for this validation task
Modified existing files (relative to the payment implementation before this task):
- src/main/java/com/bakery/payment/model/Payment.java
- src/main/java/com/bakery/payment/service/PaymentService.java
- src/main/java/com/bakery/payment/service/PaymentIntegration.java
- src/main/java/com/bakery/payment/repository/PaymentRepository.java
- src/main/java/com/bakery/payment/controller/PaymentController.java
- src/main/resources/templates/payment/make.html
- src/main/resources/templates/payment/invoice.html
- src/main/resources/templates/payment/index.html
- src/main/resources/templates/payment/admin.html
- src/main/resources/templates/payment/fragments.html
- src/test/java/com/bakery/payment/PaymentPageTests.java
- docs/PAYMENT-MANAGEMENT.md

Created:
- src/main/java/com/bakery/payment/model/PaymentValidationException.java
- src/main/resources/static/js/payment-validation.js
- src/test/java/com/bakery/payment/PaymentValidationTests.java
- src/test/js/payment-validation.test.cjs
- docs/PAYMENT-VALIDATIONS.md

The earlier payment implementation is still uncommitted, so Git lists some
modified module files as untracked. This list describes changes for this request.

## Short viva explanation
"I validate each payment twice: in the browser for quick feedback, and in Java
for security. The server checks the authenticated customer, actual order or
booking, amount, balance and transaction reference. BigDecimal keeps money
calculations accurate. Payment IDs and dates are generated by the server.
Only admins can confirm received money or record refunds. Invalid updates do
not change the saved record. File writes are validated and replaced atomically.
Invoices clearly distinguish a pending payment record from a paid receipt."

Suggested commit:
feat(payment): validate payment forms, sequential IDs, status rules and invoices

## Final verification result
Maven verify passed: 55 Java test cases, zero failures/errors, executable JAR
built. Node form tests passed: 5 tests, zero failures. Verification used installed
JDK 26 while retaining Java release target 21. Packaged application startup was
checked on a separate port: payments page 200, invalid method filter 400, anonymous
payment creation page 401, validation JavaScript asset 200. The smoke-test process
was stopped; an existing app on port 8080 was left running. Git diff --check found
no whitespace errors. No tracked sample data was changed.
