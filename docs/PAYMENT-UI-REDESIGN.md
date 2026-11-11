# Payment UI redesign — Dasun

## What changed
The existing Thymeleaf/Bootstrap module now has scoped chocolate (#6B4226),
cream (#FFF8F0), caramel (#D9A066), ivory (#FFFCF8) and charcoal (#2D2926)
styles. The shared bakery header/footer and all other module pages are unchanged.
No frontend framework, CDN, font download or production Node dependency was added.

- Make a Payment: numbered sections, required-field markers, desktop form/summary
  columns, mobile stacking, Cash/Card/Bank Transfer icon choices, live order and
  customer summary, confirmed paid/pending/available balances and current amount.
- Payment History: cleaner filters, clear-filter link, UTC dates, five distinct
  status badges, meaningful details/invoice links, empty state and mobile cards.
  When there are more than 10 results, JavaScript paginates the already-authorized
  results. Without JavaScript all records remain visible.
- Admin Dashboard: summary cards for current search/filter results, customer IDs
  and contextual links to the existing edit, status and eligible deletion forms.
  Summary cards explicitly describe their scope so filtered data is not presented
  as an application-wide total.
- Invoice: bakery branding, clear record/paid/refunded heading, customer ID,
  order/booking, date, method/reference, recorded amount, confirmed amount for this
  record and remaining balance after all confirmed receipts. Paid is never shown
  for an unconfirmed record.
- Payment actions: existing edit/status/delete forms remain on Invoice Details.
  Admin deletion uses a keyboard-accessible Bootstrap modal after the explicit
  confirmation checkbox. Without Bootstrap, the previous native dialog remains.
- Feedback: persistent dismissible success notification, inline field errors,
  first-invalid-field focus, loading indicators and disabled submit buttons.
  Browser back navigation restores a previously disabled submit button.
- Accessibility: text labels with icons, non-color status text, focus rings,
  field descriptions, reduced-motion support and larger mobile targets.
- Anonymous state: a bakery-themed welcome panel, an honest integration notice
  and a working link to the existing User Management landing page.

## Scope and functionality
Routes, service business rules, authorization, payment models, file handling,
CSRF checks and backend validation rules were not changed for this UI task.
PaymentController has one presentation-only helper that combines existing
authorized target and payment data for the form summary. This prevents pending
reservations being mislabeled as money already paid.

The payment validation script's only change is a condition allowing the enhanced
deletion modal to replace its native confirmation, with the native fallback kept.
Create/edit/status/delete still submit to the same endpoints with the same
fields and server CSRF token.

The download remains HTML. Print opens the browser print dialog, which may save
as PDF. No unsupported server PDF download button was added. Customer names and
addresses are not invented: current invoice data provides a customer ID only.

Normal browser access still requires the real IdentityProvider and PayableProvider
integrations. No public demo login, role switch or production fixture data
was added. Test adapters and browser snapshots are isolated under tests/target.

## Frontend files modified
- src/main/resources/templates/payment/fragments.html
- src/main/resources/templates/payment/index.html
- src/main/resources/templates/payment/make.html
- src/main/resources/templates/payment/admin.html
- src/main/resources/templates/payment/invoice.html
- src/main/resources/templates/payment/error.html
- src/main/resources/static/js/payment-validation.js

Frontend files created:
- src/main/resources/static/css/payment.css
- src/main/resources/static/js/payment-ui.js

Supporting files changed/created:
- src/main/java/com/bakery/payment/controller/PaymentController.java (view data only)
- src/test/java/com/bakery/payment/PaymentUiRenderingTests.java
- src/test/js/payment-ui.browser.cjs
- docs/PAYMENT-UI-REDESIGN.md

The earlier module work is still uncommitted; Git may show files from earlier
tasks as untracked. This list describes changes made for this redesign task.

## Run
Stop the existing app using Ctrl+C, then from the repository root:
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-26'
    .\mvnw.cmd spring-boot:run

Open http://localhost:8080/payments and refresh. The existing Java 21 release
target is unchanged; this computer's installed JDK 26 is used for verification.

## Repeat verification
    .\mvnw.cmd verify
    node --test src/test/js/payment-validation.test.cjs

The Maven UI rendering test writes actual Thymeleaf output using trusted
test-only identities and temporary payment records to target/payment-ui-preview.
For optional browser checks install only ignored local test tooling:
    npm install --prefix target/ui-tools --no-save --package-lock=false playwright
    node src/test/js/payment-ui.browser.cjs

Browser automation uses installed Chrome; override PAYMENT_BROWSER with another
Chromium executable path if needed. It intercepts network calls using those
test-generated pages and local assets. It checks layout and frontend behavior,
not a live authenticated deployment. Backend HTTP/CRUD behavior is checked by
the Spring MVC and service tests. This distinction matters because User Management
and order/booking services are not implemented in this scaffold.

Desktop/mobile screenshots and a printed invoice PDF are generated under the
ignored target/payment-ui-preview folder. They use fictional test records.
No screenshots, test browser tooling or test payment records need to be committed.

Suggested commit:
style(payment): redesign payment dashboards, forms and printable invoices

## Final verification results
- Maven verify: 56 Java cases passed; zero failures/errors; executable JAR built.
- Node validation suite: 5 tests passed.
- Chrome: 44 layout combinations across 1440px, 768px, 390px and 320px widths
  passed without horizontal page overflow.
- Browser interactions passed: pagination, search/filter query submission,
  required-field errors and correction, live owner/paid/available summary,
  Cash/Card selection, create/edit/status/delete payloads to existing endpoints,
  modal cancellation and confirmation, disabled-button/loading state, print
  invocation and print-only invoice visibility.
- Browser console/page errors: zero.
- A4 invoice-print.pdf and desktop/mobile PNG screenshots were generated in
  target/payment-ui-preview. Desktop form, mobile admin, mobile invoice and
  anonymous welcome screenshots were visually reviewed.
- Git diff --check: no whitespace errors. Other members' modules and tracked
  sample data were not changed.

Limits: browser navigation and POST responses use the real test-rendered HTML
with intercepted routes, not live authenticated application sessions. Server
ownership, authorization, validation, storage and CRUD are exercised separately
by the Java tests. Full integrated browser acceptance requires the other module
owners to provide production authentication and order/booking adapters. The
browser print dialog itself cannot be operated in headless mode; its invocation,
print CSS and PDF output were verified. There is no server PDF download endpoint.

## Supplied HTML reference adaptation
Adapted the visual design from the user's Downloads/bakery-payment-ui.html into
the existing Java-backed templates. Added its compact Bakery Payments branding,
left sidebar, cream order-summary panel and rounded white cards. On smaller
screens the sidebar becomes wrapping navigation and the content stacks.

The prototype's localStorage code, fictional seed orders, public admin controls
and client-side status/persistence logic were not imported. Real Java endpoints,
session authorization, CSRF protection, validation and payments.txt remain in use.
Invoices navigation opens payment history for record selection, or the currently
viewed invoice. Admin navigation remains restricted to the existing trusted role.
Shared group-project navigation is preserved.

Changed for this adaptation: payment/fragments.html, index.html, make.html,
admin.html, invoice.html, error.html and static/css/payment.css. No Java, payment
validation or storage code changed in this adaptation.

Verification repeated after adaptation: Maven verify passed all 56 Java cases;
all 5 JavaScript validation tests passed; all 44 responsive browser combinations
and the browser interaction checks passed; zero console/page errors. Desktop
form and mobile anonymous-state screenshots were visually reviewed. The same
test-fixture/live-integration limitations documented above still apply.

## Optional local demo is now available
A later explicit request added a local-only payment-demo profile, documented in
PAYMENT-DEMO.md. That opt-in profile supplies fictional accounts/payable targets
and a visible account selector. Normal mode remains fail closed with no public
demo login. The demo stores records separately and labels printed/downloaded
invoices as fictional.
