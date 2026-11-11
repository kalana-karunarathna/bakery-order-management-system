# Local Payment Demo — Dasun

The payment-demo profile lets you demonstrate the real payment module while the
group's User, Order and Cake modules are still placeholders. It is not production
authentication and must not be enabled on a shared or public deployment.

## Open it now
The verified demo is running at:
http://localhost:8080/payments

Refresh with Ctrl+F5. It starts as Customer 1. The yellow LOCAL DEMO panel contains
a Demo account dropdown and Switch account button. Select Customer 1, Customer 2
or Admin. Switching uses a CSRF-protected POST and rotates the session ID.

- Customer 1 owns DEMO-O001 (LKR 5,000) and DEMO-B001 (LKR 8,500).
- Customer 2 owns DEMO-O002 (LKR 3,000) and DEMO-B002 (LKR 7,000).
- Admin can see both customers' records and use existing administrator operations.

## Try the complete workflow
1. As Customer 1, click Make Payment.
2. Select BOOKING, DEMO-B001, Advance, LKR 2,000 and Cash. Leave reference blank
   for an automatically generated receipt reference. Confirm Payment.
3. Inspect the Pending invoice and edit its amount/reference if needed.
4. Select Admin in the yellow panel and click Switch account.
5. Open the payment, select Paid - independently verified, and Update status.
   This only demonstrates a manual confirmation, not actual received funds.
6. The remaining confirmed balance is LKR 6,500 for an unchanged LKR 2,000 deposit.
7. Print or download the invoice. It remains visibly labeled FICTIONAL DEMO.
8. Switch to Customer 2. Customer 1's payment is absent from history; opening its
   invoice URL returns 404. Customer 2 sees only their own sample payable targets.
9. To test delete, create another Pending record, cancel it as its owner, switch
   to Admin, and use Delete record with the checkbox and confirmation modal.
10. Paid/Refunded records cannot be deleted. As Admin, a Paid record may be marked
    Refunded after a simulated/manual demonstration, restoring its payable balance.
11. Stop/restart using the same profile and working directory to retain records.

Only fake receipt/transfer references belong in this demo. Never enter real card
numbers, CVVs, banking credentials or personal customer data.

## Persistence and isolation
Demo payments use data-local/payment-demo/payments.txt, outside tracked samples.
The file is created on the first valid payment. Normal mode still uses its
existing configured data directory. There is no browser localStorage persistence.

The demo providers are active ONLY with the explicit payment-demo Spring profile.
The normal application's fail-closed identity/payable defaults remain unchanged.
No code in User, Order or Cake modules was modified. The optional demo
CustomerDirectory recognizes only the two fictional customer accounts.

The profile binds to 127.0.0.1. Startup rejects an overridden non-loopback binding.
A payment-route filter also rejects non-loopback remote addresses and unexpected
Host names. The demo must be used directly through localhost/127.0.0.1, not via
a public tunnel or reverse proxy.

## Run again from IntelliJ terminal
The current background demo already occupies port 8080; do not start a second
instance on that port. Stop the existing bakery Java process before building or
starting another one. Windows can lock a running JAR during Maven repackage.

From the repository root:
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-26'
    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=payment-demo"

Or, after a successful build:
    java -jar target/bakery-order-management-system-0.0.1-SNAPSHOT.jar --spring.profiles.active=payment-demo

In IntelliJ's Application run configuration add:
    --spring.profiles.active=payment-demo
to Program arguments, with the repository root as Working directory.
The existing Java 21 release target is unchanged; local verification used JDK 26.

For normal integration mode, omit the profile argument. The account selector,
sample payable providers and demo controller are then absent.

## Verification
Maven verify passed all 61 Java cases with zero failures/errors.
This includes the existing 56 cases plus tests for demo CRUD, ownership,
customer confirmation rejection, refunds, persistence, CSRF, local/Host guards,
and proof that demo beans do not exist in the default application profile.

A real Chrome browser exercised the running demo on isolated port 18085:
create, edit, account switching, customer-to-customer invoice denial, unauthorized
confirmation rejection, admin confirmation/refund, cancellation and modal deletion,
download watermark and four widths (1440/768/390/320). All checks passed.
Test data lived only in target/payment-demo-browser-data. The temporary server
was stopped. The user-facing demo on 8080 starts with its separate persistent
demo directory, without those browser test transactions.

The browser test is src/test/js/payment-demo.browser.cjs. It uses optional
Playwright installed only under ignored target/ui-tools. It needs a separately
running demo at http://127.0.0.1:18085, or PAYMENT_DEMO_URL pointing to an isolated
demo instance. Do not point destructive CRUD tests at data you want to keep.

## Files for this change
Created:
- src/main/java/com/bakery/payment/demo/PaymentDemoConfiguration.java
- src/main/java/com/bakery/payment/demo/PaymentDemoController.java
- src/main/java/com/bakery/payment/demo/PaymentDemoAdvice.java
- src/main/resources/application-payment-demo.properties
- src/test/java/com/bakery/payment/PaymentDemoTests.java
- src/test/java/com/bakery/payment/PaymentDemoIsolationTests.java
- src/test/js/payment-demo.browser.cjs
- docs/PAYMENT-DEMO.md

Modified:
- src/main/resources/templates/payment/fragments.html
- src/main/resources/templates/payment/invoice.html
- src/main/resources/static/css/payment.css
- docs/PAYMENT-UI-REDESIGN.md

Suggested commit:
feat(payment): add isolated localhost demo profile for assignment walkthroughs
