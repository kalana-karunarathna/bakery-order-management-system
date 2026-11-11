# Payment Management integration

How Payment and Invoice Management (originally built on `feature/payment-management`) is connected
to User Management, Order Management and MongoDB. The payment module's own rules and UI are described
in [PAYMENT-MANAGEMENT.md](PAYMENT-MANAGEMENT.md); this document covers the integration only.

## Architecture

```
Browser ──► Spring Security (SecurityConfig) ──► PaymentController ──► PaymentService ──► PaymentRepository
                                                     │                    │                 ├─ FilePaymentRepository   (bakery.storage.type=file)
                                                     │                    │                 └─ MongoPaymentRepository  (bakery.storage.type=mongodb)
                                                     │                    ├─ IdentityProvider  ◄── SecurityPaymentIdentity (CurrentUserService)
                                                     │                    ├─ PayableProvider   ◄── OrderPayableProvider   (OrderService)
                                                     │                    └─ CustomerDirectory ◄── UserCustomerDirectory  (UserRepository)
                                                     └─ PaymentLayoutAdvice (storefront or admin page shell)

OrderService ──► OrderPaymentGuard ◄── PaymentOrderGuard (PaymentRepository)
```

The payment module was written against small "ports" (`PaymentIntegration`). The integration only
adds adapters for them (package `com.bakery.payment.integration`), so the payment business rules are
unchanged. Order Management gets a matching port, `OrderPaymentGuard`, so it never depends on payment classes.

## Security

| Route | Who |
|---|---|
| `/payments`, `/payments/new`, `/payments/{id}`, `/payments/{id}/download`, `/payments/{id}/edit`, `/payments/{id}/status` | Signed-in users. Customers only ever see and change **their own** payments (checked in `PaymentService`; another customer's payment is reported as "not found"). |
| `/payments/admin`, `/payments/{id}/delete` | ADMIN only (SecurityConfig). |
| Confirm (Paid), Failed, Refunded | ADMIN only (enforced in `Payment.transition` / `PaymentService`). Customers may only cancel their own pending payment. |

* Identity always comes from the Spring Security session through `CurrentUserService` (active users only);
  customer IDs, totals, dates and statuses posted in forms are ignored.
* Spring Security CSRF protection stays on. Payment forms use `th:action`, so they carry Spring's `_csrf`
  token automatically, plus the payment module's own form token.
* No card numbers, CVVs or bank credentials are collected. There is no payment gateway and nothing simulates
  a successful transaction: every payment starts as **Pending** and only an administrator can mark it **Paid**
  after verifying the money independently.

## Payment methods and references (manual workflow)

There is no payment gateway. Customers record how they paid; an administrator checks the money before the
payment counts.

| Method | Meaning | Reference field | Admin checks before marking Paid |
|---|---|---|---|
| Cash | Paid at the counter or to the delivery rider | none (any value sent is ignored) | cash received and counted |
| Card | Paid on the bakery's own card machine (shop or delivery) | "Terminal transaction reference (optional)": the reference/approval code on the slip | card machine report / settlement (by slip reference, or by amount, date and time) |
| Bank transfer | Sent from the customer's bank to the bakery's account | "Bank transfer reference (recommended)": "Reference number" / "Transaction ID" in the banking app or deposit slip | bank statement (by reference, or by amount, date and customer name; otherwise mark Failed) |

* Without a reference the server stores a **system reference** (`CASH-PAY001`, `CARD-PAY002`, `TRANSFER-PAY003`).
  Customers cannot type this format, so the invoice and admin page can show "Not provided - verified manually".
* **Card details are never accepted or stored.** For card payments, any run of 13+ digits (spaces/hyphens ignored),
  an expiry date (MM/YY) or CVV/expiry wording is refused; for bank transfers, 13-19 digit numbers that pass the card
  checksum (Luhn) are refused. The rejected text is not shown back on the page or logged. The same rules run in the
  browser (`payment-validation.js`) and on the server (`Payment.customerReference`, authoritative).
* **Every new payment is Pending.** Only an administrator can mark it Paid, and only after ticking
  "I have verified LKR … was received" (`verified=true`, checked in `PaymentService`). A missing reference never skips
  this step. Customers can only cancel their own pending payments.
* Existing records are not changed: old references are kept, and editing a cash payment keeps its reference.

## Admin CRUD and the coursework delete switch

| Operation | Where | Who | Route |
|---|---|---|---|
| Create | Make a Payment (`/payments/new`) | customer (own orders) or admin | `POST /payments` |
| Read | Payment History, Admin Payment Dashboard, invoice | owner or admin | `GET /payments`, `/payments/admin`, `/payments/{id}` |
| Update | invoice page: edit amount/reference, change status | owner (pending edits, cancel) or admin (Paid needs verification, Failed, Refunded) | `POST /payments/{id}/edit`, `POST /payments/{id}/status` |
| Delete | **Delete** in the dashboard's Actions column (or the invoice page) | ADMIN only (SecurityConfig + PaymentService) | `POST /payments/{id}/delete` with CSRF and `confirmed=true` |

* **Production-safe default:** `bakery.payments.hard-delete.enabled=false` (in `application.properties`, variable
  `BAKERY_PAYMENT_HARD_DELETE`). Only Failed or Cancelled payments can be deleted; Paid, Pending and Refunded
  records are kept as financial history.
* **Coursework / university demo:** start with the `coursework` profile (`application-coursework.properties`) or
  `BAKERY_PAYMENT_HARD_DELETE=true`. A Delete button is then shown for **every** status, and a warning is logged at
  start-up.
* Delete flow: Delete → dialog showing Payment ID, amount and status → "Yes, delete payment" posts the form (Spring
  CSRF token + payment form token + `confirmed=true`) → the record is removed from the configured store (TXT file or
  the MongoDB `payments` collection) → redirect to the dashboard with a success message. Without the confirmation the
  server refuses. Each deletion is logged with payment ID, status, order and admin ID (no personal data).
* Payment IDs come from a counter that never goes back, so deleted IDs are never reused.
* Order paid amount, outstanding balance, payment status and edit/cancel protection are always calculated from the
  remaining payment records, so they update immediately. An order keeps its delete protection while any payment
  record (even Refunded or Failed) still exists.

## Orders and payments

* **Payable orders** come from `OrderService` on every request: customers get their own orders, admins every
  order; **cancelled orders cannot be paid**. Custom cake bookings are not offered until Custom Cake Booking
  provides a service (the "Custom cake booking" option is hidden until then).
* **Amount due** = `order.calculateTotal()` (includes the delivery fee), converted with
  `BigDecimal.valueOf(total).setScale(2, HALF_UP)`, so `3 × 1.10` is exactly `3.30`.
* **Overpayment** is impossible: Pending payments reserve their amount, and Pending + Paid can never exceed the
  order total (checked in `PaymentService` and again when records are stored).
* **Order protection** (`OrderService` via `OrderPaymentGuard`):

  | Order has… | Customer edit / cancel | Admin cancel | Admin delete |
  |---|---|---|---|
  | no payments | allowed (while Pending) | allowed | allowed |
  | only Failed / Cancelled / Refunded payments | allowed | allowed | **refused** (payment history is kept) |
  | a Pending or Paid payment | **refused** | **refused** | **refused** |

  The order pages hide Edit / Cancel for such orders and explain why. If payment data cannot be read, the
  change is refused (fail closed).
* **Payment summary on the order pages** (Order Detail card and a Payment column in My Orders), calculated on
  the server from the payment records every time (nothing is stored on the order):

  | Shown | Calculation |
  |---|---|
  | Confirmed paid | sum of **Paid** payments only |
  | Awaiting confirmation | sum of **Pending** payments (not paid yet, but reserved) |
  | Refunded | sum of **Refunded** payments (not counted as paid) |
  | Outstanding balance | order total − confirmed paid |
  | Status | `PAID` (paid ≥ total), `PARTIALLY_PAID` (some paid), `PAYMENT_PENDING` (nothing paid, something pending), `UNPAID` |

  "Pay remaining balance" appears for the owner only when something can still be paid (outstanding minus
  pending > 0) and the order is not cancelled; it opens the payment form with that amount filled in. The summary
  is only built after the normal owner/admin check, so another customer never sees it.
* **Order status and payment status are separate.** Confirming a payment never changes the order status, and
  the kitchen workflow (Pending → Preparing → Ready → Delivered) works whether or not the order is paid.

## Storage

`bakery.storage.type` (environment variable `BAKERY_STORAGE`) selects the store for users, orders **and**
payments, exactly as before:

* `file`: `payments.txt` in `bakery.data-directory` (the payment module's original format).
* `mongodb` (default): the `payments` collection in the database of `MONGODB_URI`.

Old TXT payment records are **not** migrated automatically.

### MongoDB collection `payments`

```json
{
  "_id": "PAY001",
  "targetType": "ORDER", "targetId": "O001", "customerId": "U002",
  "total":  { "$numberDecimal": "3250.00" },
  "amount": { "$numberDecimal": "3250.00" },
  "method": "BANK_TRANSFER", "purpose": "FULL", "status": "PENDING",
  "reference": "TRF-2026-0001", "referenceKey": "TRF-2026-0001",
  "requestKey": "<uuid from the form>",
  "createdAt": ISODate("..."), "updatedAt": ISODate("..."),
  "version": 0
}
```

* Money is **Decimal128**. The invoice number is derived (`INV-PAY001`) and not stored.
* IDs come from the shared `counters` collection (`_id: "payments"`), are unique under concurrency and never reused.
* Indexes: `uk_payments_request_key` (unique: a form can only be submitted once), `uk_payments_reference`
  (unique, case-insensitive through `referenceKey`), `idx_payments_customer_created`, `idx_payments_target_status`,
  `idx_payments_status_created`.
* A `$jsonSchema` validator rejects malformed documents. A stored record that breaks a domain rule blocks payment
  changes until it is fixed (only its ID is logged); it is never silently skipped, because skipping could hide money.
* Concurrency: every write runs while holding a short lease document in `payment_locks` (expires after 15 s if an
  instance crashes), and every document has a `version` for optimistic locking.
* Start-up creates the collection, validator and indexes if missing. It never deletes or overwrites data.

## Demo profile

`payment-demo` is disabled by default. When enabled explicitly it:

* replaces sign-in **for `/payments/**` only** with fictional personas (all other pages keep normal security);
* still enforces Spring Security CSRF;
* refuses to start unless `server.address=127.0.0.1` and `bakery.storage.type=file`, and only answers local requests;
* stores fictional records in `data-local/payment-demo/` (never MongoDB).

## Tests

| Test class | Covers |
|---|---|
| `PaymentIntegrationTests` | Sign-in and admin rules, CSRF, real orders and server totals, rounding, overpayment, duplicate submission, ownership/privacy, admin confirm/refund, order protection, order page links |
| `MongoPaymentRepositoryTests` | Decimal128 storage, indexes, validator, restart and ID sequence, concurrent overpayment attempts, duplicate request keys and references, fail-closed corrupted data, web flow with MongoDB |
| `PaymentTests`, `PaymentValidationTests`, `PaymentPageTests`, `PaymentUiRenderingTests`, `PaymentDemoTests`, `PaymentDemoIsolationTests` | The payment module's original tests, updated for Spring Security |
| `src/test/js/*.cjs` | Client-side validation (`node --test src/test/js/payment-validation.test.cjs`) and browser checks (need Playwright under `target/ui-tools`) |

MongoDB tests use a random local `bakery_test_xxxxxxxx` database that is dropped afterwards; they never use
`bakery_db` or Atlas, and are skipped if no local MongoDB is running.

## Known limitations

* Payments are recorded manually; there is no gateway integration.
* A payment and an order edit submitted at the very same moment can still race (the order check runs just before
  the order is updated). The payment then keeps the old total and an admin cannot confirm it ("Order details
  changed"), so no money is recorded wrongly; the admin marks it Failed.
* MongoDB writes of one unit of work are not a multi-document transaction (each operation changes one payment).
* Unit prices are still entered on the order form until Product Management supplies prices.
