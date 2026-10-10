# Sample text storage

All six .txt files contain fictional examples, not live records.
They are tracked so every team member starts with the same format.
Nothing reads or rewrites them during application startup.

| File | Initial columns |
| --- | --- |
| users.txt | `id\|name\|email\|role` |
| products.txt | `id\|name\|category\|price\|available` |
| orders.txt | `id\|userId\|productId\|quantity\|status` |
| cake-bookings.txt | `id\|userId\|flavour\|size\|message\|pickupDate\|status` |
| payments.txt | `id\|orderId\|amount\|method\|status\|invoiceId` |
| feedback.txt | `id\|userId\|productId\|rating\|comment\|moderationStatus` |

## Format

- UTF-8 encoding; one record per line.
- Lines beginning with `#` are comments, including the column header.
- Fields are separated by `|`. When repositories are implemented, skip blank
  lines and comments, and split records with `line.split("\\|", -1)`.
- These samples have no escaping convention. Initially reject pipe characters
  and line breaks in user-entered fields, or agree on an escaping format before
  implementing free text.
- IDs link examples across files. Dates use ISO `yyyy-MM-dd`; prices use a
  decimal point. Use `BigDecimal` for monetary values in future models.
- Order items, invoices, admin roles, and other domain details are starter
  examples only. Agree on changes to the schemas as a team.

## Shared utility

Inject `com.bakery.common.TextFileStorage` into future repositories through a
constructor. `readLines("products.txt")` returns every raw line, including
comments. Missing files raise `IOException`; reading does not create files.
Repositories decide how to parse records and report storage errors.

`writeLines("products.txt", lines)` creates the configured directory if needed
and replaces the entire file with the supplied UTF-8 lines. Include any comment
headers you want to retain. This method is never called by the landing pages.

Only simple .txt filenames are accepted, such as `cake-bookings.txt`.
Paths, subdirectories, and other extensions are rejected. The configured
directory is a trusted local directory; keep symbolic links out of it.

This is a basic educational I/O helper. It has no atomic replacement,
cross-file transactions, or read-modify-write locking. Before adding concurrent
CRUD, coordinate the entire read-modify-write operation in a shared repository
or service and decide how to handle failed writes. Do not run multiple
application instances against the same files.

For personal development, copy the examples to the ignored `data-local/`
directory and set `BAKERY_DATA_DIRECTORY=data-local`. No files are seeded into
a new directory automatically. Keep real user and payment details out of Git.
