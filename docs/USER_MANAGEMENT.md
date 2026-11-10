# User Management (Component 01)

Customer registration, sign-in, profiles, admin customer management and application-wide
security (Spring Security). Package: `com.bakery.user`.

## Routes

| Route | Who | Purpose |
| --- | --- | --- |
| `GET /users` | Everyone | Account landing page |
| `GET/POST /register` | Anonymous | Customer registration |
| `GET /login`, `POST /login` | Anonymous | Sign-in (POST handled by Spring Security) |
| `POST /logout` | Signed in | Sign-out (CSRF-protected POST only) |
| `GET /profile` | Signed in | Own profile |
| `GET/POST /profile/edit` | Signed in | Edit own name, email, phone, address |
| `GET/POST /profile/password` | Signed in | Change password (current password required) |
| `GET/POST /profile/delete` | Signed in | Delete own account (password required) |
| `GET /users/admin` | ADMIN | Admin dashboard: counts, newest customers, module links |
| `GET /users/admin/customers?q=&status=` | ADMIN | Customer list, search, status filter, counts |
| `GET /users/admin/{id}` | ADMIN | Customer details |
| `POST /users/admin/{id}/edit` | ADMIN | Update details and customer type |
| `POST /users/admin/{id}/status` | ADMIN | Activate / deactivate (`active=true/false`) |
| `POST /users/admin/{id}/delete` | ADMIN | Delete (anonymise) customer |
| `GET /access-denied` | Everyone | 403 page |

No `/profile` route takes a user ID. The account is always the one in the authenticated session.

## Sign-in and navigation

**One sign-in form for everyone** (`/login`, Spring Security, BCrypt). After a successful sign-in
(`RoleBasedLoginSuccessHandler`):

| Role | Goes to |
| --- | --- |
| ADMIN | `/users/admin` (admin dashboard) |
| CUSTOMER | `/profile` |
| Either, after being sent to sign in from a protected page | back to that page (e.g. `/profile/password`), unless it is an admin page and the user is a customer, in which case `/profile` |

Signed-in users who open `/login` or `/register` are sent to `/profile`.

**Authentication pages** (`/login`, `/register`) use the minimal `user/fragments/user-ui :: auth-header`
(brand + "Back to shop") and `auth-footer` instead of the module navigation.

**Shared header** (`fragments/layout :: header`) is role-aware:

| State | Header shows |
| --- | --- |
| Visitor | Home · Shop (`/products`) · Custom Cakes (`/cake-bookings`) · Sign in · Create account |
| CUSTOMER | Home · Shop · Custom Cakes · My Orders · account menu (name, email, My account, My orders, Log out) |
| ADMIN | Admin dashboard · Customers · View site · account menu (Admin dashboard, Admin profile, Log out) |

The shared footer links to all six module pages for everyone, so other modules stay reachable.

**My Orders** points to `${@navigation.myOrdersUrl}` (`NavigationLinks`), configured by
`bakery.nav.my-orders-url`. On this branch it is `/orders` (the page that exists here).
`/orders/my-orders` only exists on `feature/order-management`; after merging, set
`bakery.nav.my-orders-url=/orders/my-orders`.

**Admin layout** (`templates/fragments/admin.html` + `static/css/admin.css`): dark top bar and a
sidebar (Dashboard, Customers, Products, Orders, Custom Cake Bookings, Payments, Feedback,
Admin profile, Log out). The module entries link to each module's existing page until it has admin
routes. Other modules can reuse the fragments; see the comment at the top of `admin.html`.
The admin's own profile (`/profile`) uses the shared header in its admin state.

Hiding links is not security: every admin route is still protected by `SecurityConfig` and
`@PreAuthorize("hasRole('ADMIN')")`.

## OOP design

| Concept | Where |
| --- | --- |
| Inheritance | `User` (abstract) → `RegularCustomer`, `LoyaltyCustomer`, `AdminUser` |
| Encapsulation | `User` has only private fields; changes go through validating methods (`changeEmail`, `changePasswordHash`, `activate`, `markDeleted`…). Role is decided by the subclass, never by input. |
| Abstraction | `DiscountPolicy` interface: `getDiscountRate()`, `calculateDiscount(subtotal)`, `applyDiscount(subtotal)` |
| Polymorphism | `RegularCustomer` → 0%, `LoyaltyCustomer` → 5%, `AdminUser` → 0%. `UserRepository` builds the right subclass from the stored role/type; `UserView` calls `calculateDiscount` without knowing the subclass. |

The 5% loyalty rate is a demonstration value (`LoyaltyCustomer.DISCOUNT_PERCENT`). It is **not applied
to any order** yet; Order Management must integrate it (see below). Customers become loyalty members
only when an admin changes their customer type.

## Data file: `users.txt`

UTF-8, one account per line, `|`-separated, 10 fields:

```
# id|role|customerType|fullName|email|phone|address|passwordHash|status|createdDate
U002|CUSTOMER|REGULAR|Nimal Perera|nimal@example.com|077 123 4567|12 Lake Road, Kandy|$2a$10$…|ACTIVE|2026-11-10
```

- `role`: `CUSTOMER` or `ADMIN`. `customerType`: `REGULAR`, `LOYALTY`, or `NONE` (admins).
- `passwordHash`: BCrypt only, or `-` for "no password" (cannot sign in).
- `status`: `ACTIVE`, `INACTIVE`, `DELETED`. Deleted accounts keep their ID with personal data erased.
- `|`, line breaks and other control characters are rejected in every field, so records cannot be split.
- Writes are atomic (temporary file + move) and every read-modify-write runs under one lock.
- Unreadable lines are kept unchanged at the end of the file and their IDs are never reused.

### Migration from the scaffold format

Old format: `id|name|email|role` (no password). When such a line is read it becomes an **INACTIVE
account without a password**: it can never sign in and an admin cannot activate it. On the first save
the original file is copied once to `users-legacy-backup.txt` in the same folder. The tracked
`data/users.txt` sample is left in the old format; it is converted automatically the first time
the app saves an account.

Tip: run locally against a copy (`BAKERY_DATA_DIRECTORY=data-local`) so real hashes never end up in
the tracked `data/users.txt`.

## Security

- Spring Security form login (`email` + `password`), BCrypt (`BCryptPasswordEncoder`, cost 10).
- Session fixation protection (`changeSessionId`), 30-minute session timeout, `SameSite=Lax` cookie.
- Logout: POST `/logout` with CSRF token; session invalidated, `JSESSIONID` deleted.
- Default security headers, including `Cache-Control: no-store` (Back button cannot show protected pages after sign-out).
- CSRF on all state-changing requests. `th:action` forms get the token automatically.
- The disabled-account message only appears after the correct password, so it cannot be used to
  discover registered emails. Unknown emails and wrong passwords give the same message.
- `AccountStatusFilter` signs out a user on their next request if an admin deactivated or deleted them.
- Admin pages are protected by URL rule **and** `@PreAuthorize("hasRole('ADMIN')")`.
- Admin accounts cannot be edited, deactivated or deleted from the dashboard; the last active admin
  cannot delete their own account.
- Templates receive `UserView` (no password hash field). Logs never contain passwords or hashes.

### Temporary access rules (must be revisited)

`SecurityConfig.OTHER_MODULE_ROUTES` keeps `/products/**`, `/orders/**`, `/cake-bookings/**`,
`/payments/**` and `/feedback/**` **public** so the other modules keep working while they have no
sign-in integration. This is not secure for their admin/management actions. Each module should add
its own rules when integrating, for example:

```java
.requestMatchers("/orders/admin", "/orders/*/status", "/orders/*/delete").hasRole("ADMIN")
.requestMatchers("/orders/place", "/orders/my-orders", "/orders/*/edit", "/orders/*/cancel").authenticated()
```

Any form that posts with a plain `action="..."` (not `th:action`) will be rejected with 403 because
it has no CSRF token. Use `th:action`.

Not implemented (out of scope): login rate limiting/lockout, password reset by email, remember-me,
"sign out other sessions" after a password change.

## First admin account (development only)

There is no default admin and no public admin sign-up. Create one locally from environment
variables; nothing is created unless `BAKERY_ADMIN_BOOTSTRAP=true`. Keep local accounts in the
Git-ignored `data-local/` folder so password hashes never reach the tracked `data/` folder.

### 1. One-time setup (Windows PowerShell, repository root)

```powershell
# Local data folder (ignored by Git). Skip the copy if data-local already exists.
if (-not (Test-Path data-local)) { Copy-Item data data-local -Recurse }
$env:BAKERY_DATA_DIRECTORY = 'data-local'

# Bootstrap variables for this terminal only.
$env:BAKERY_ADMIN_BOOTSTRAP = 'true'
$env:BAKERY_ADMIN_EMAIL = 'admin@bakery.local'       # any email not already registered
$env:BAKERY_ADMIN_NAME = 'Bakery Admin'              # optional
$secure = Read-Host 'Choose an admin password (8+ chars, a letter and a number)' -AsSecureString
$env:BAKERY_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password

.\mvnw.cmd spring-boot:run
```

Wait for `Admin bootstrap created administrator U00x` in the console, stop the app (Ctrl+C), then
remove the bootstrap variables so they are not reused:

```powershell
Remove-Item Env:BAKERY_ADMIN_PASSWORD, Env:BAKERY_ADMIN_BOOTSTRAP, Env:BAKERY_ADMIN_EMAIL -ErrorAction SilentlyContinue
```

### 2. Every later run

```powershell
$env:BAKERY_DATA_DIRECTORY = 'data-local'
.\mvnw.cmd spring-boot:run
```

### 3. Test admin sign-in

Open http://localhost:8080/login, sign in with the admin email and password. You should land on
`/users/admin` (dashboard) with the dark admin top bar and sidebar. Open **Customers** to manage
accounts. Then sign in as a customer in a private window and check that `/users/admin` shows the
403 page.

### What the bootstrap does in each situation

| Situation | Result |
| --- | --- |
| `BAKERY_ADMIN_BOOTSTRAP` not `true` | Nothing runs. |
| Email or password missing | Warning, nothing created, app still starts. |
| Weak or invalid password/email | Warning naming the rule that failed (never the password), nothing created. |
| An admin with that email already exists (bootstrap done before) | Info message, nothing changed; sign in with the existing password. The new password value is ignored. |
| That email belongs to a customer | Warning, the customer is **not** promoted. Use another email. |

Forgot the local admin password? There is no reset flow yet. In `data-local/users.txt`, change that
admin line's status to `INACTIVE` (or delete the line), then run the bootstrap again with a
**different** email. Never edit the tracked `data/users.txt` for this.

### Create and test a customer account

1. Open http://localhost:8080/register (or **Create account** in the header).
2. Fill in name, email, phone, address and a password (8+ characters with a letter and a number).
3. You are sent to the sign-in page; sign in and you land on **My account** (`/profile`).
4. Try **Edit details**, **Change password**, then **Log out** from the account menu. The old password
   now fails and the new one works.

## Integration contract for other modules

Inject `com.bakery.user.service.CurrentUserService`:

```java
Optional<AuthenticatedUser> me = currentUserService.currentUser();   // empty if anonymous
AuthenticatedUser me = currentUserService.requireCurrentUser();      // AccessDeniedException if anonymous
boolean mine = currentUserService.isCurrentUser(order.getCustomerId());
```

`AuthenticatedUser` = `id`, `email`, `fullName`, `role`, `customerType`, `status`, `isAdmin()`.
It is read fresh from `users.txt` on each call. Alternatively use
`@AuthenticationPrincipal BakeryUserPrincipal principal` in a controller.

### Premium storefront header (on `feature/order-management`)

The storefront header in `templates/fragments/storefront.html` currently shows a disabled cart and an
Account icon linking to `/users`. When the branches are merged, give it the same auth states as the
shared header (add `xmlns:sec="http://www.thymeleaf.org/extras/spring-security"`):

```html
<!-- Visitor -->
<a sec:authorize="!isAuthenticated()" th:href="@{/login}">Sign in</a>
<a sec:authorize="!isAuthenticated()" th:href="@{/register}">Create account</a>
<!-- Customer: name + menu -->
<div sec:authorize="hasRole('CUSTOMER')"> <span sec:authentication="principal.fullName"></span>
    <a th:href="@{/profile}">My account</a> <a th:href="@{/orders/my-orders}">My orders</a> ... </div>
<!-- Admin -->
<a sec:authorize="hasRole('ADMIN')" th:href="@{/users/admin}">Admin dashboard</a>
<!-- Logout must be a POST form (CSRF token via th:action) -->
<form sec:authorize="isAuthenticated()" th:action="@{/logout}" method="post"><button>Log out</button></form>
```

Keep the "My Orders" item hidden for visitors, and use `th:action` on every form.
Thymeleaf 3.1 does not allow `${param...}` or `${@bean...}` directly inside `th:attr` or `@{...}`
links; read them into a variable with `th:with` first (see `layout.html`).

### What Order Management should change (not done in this task)

Order Management currently accepts a typed `customerId`; this is **not secure**. When integrating:

1. Take the customer ID from `currentUserService.requireCurrentUser().id()`; remove the
   `customerId` form field and request parameter.
2. `/orders/my-orders` lists only that customer's orders.
3. Order detail / edit / cancel check `order.getCustomerId()` equals the signed-in ID (or the user is ADMIN); otherwise 403.
4. Status updates and deletion become ADMIN-only (URL rules in `SecurityConfig` plus a service check).
5. To apply loyalty discounts, load the customer and call `calculateDiscount(subtotal)`; store the
   discount on the order rather than recalculating old orders.
6. Set `bakery.nav.my-orders-url=/orders/my-orders` so the header's "My Orders" opens the real page,
   and point the admin sidebar's "Orders" entry (`fragments/admin.html`) at `/orders/admin`.
7. Replace the temporary public `/orders/**` rule in `SecurityConfig.OTHER_MODULE_ROUTES` with
   role rules (see "Temporary access rules" above). Until then the site is **not** fully secured.
