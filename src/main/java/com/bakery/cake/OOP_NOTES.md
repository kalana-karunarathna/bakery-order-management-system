# OOP Notes: Custom Cake Booking Module (`com.bakery.cake`)

This document maps each Object-Oriented Programming (OOP) concept required by the university specification directly to the classes, methods, and design structures in `com.bakery.cake`. Use the explanations below during the oral viva examination.

---

## 1. Quick Mapping Table

| OOP Concept | Class / Interface | Method / Field / Construct | Viva Explanation |
| :--- | :--- | :--- | :--- |
| **Abstraction** | `CustomCake` | `public abstract BigDecimal calculatePrice();`<br>`public abstract String getCakeType();` | We cannot instantiate a generic "custom cake" directly because price and type name depend on the specific cake category. An abstract class defines *what* behaviors must exist without prescribing *how* each category calculates them. |
| **Abstraction** | `BookingRepository` | Interface: `findAll()`, `findById()`, `findByCustomerId()`, `save()`, `update()`, `delete()` | Separates the service layer from persistence mechanics. The service only knows the repository contract, allowing file storage to be swapped for a database without changing business logic. |
| **Abstraction** | `CurrentCustomerProvider` | Interface: `getCurrentCustomerId(HttpSession)`, `isAdmin()`, `isAuthenticated()` | Decouples the Cake Booking module from the User Management module and Spring Security. The cake module only depends on customer ID and role contracts, unaware of authentication internals. |
| **Inheritance** | `BirthdayCake`<br>`WeddingCake`<br>`AnniversaryCake`<br>`GeneralCustomCake` | `extends CustomCake`<br>`super(flavour, weightKg, tiers, ...)` | Establishes a true **"IS-A"** relationship. Each specific cake inherits common attributes (`flavour`, `weightKg`, `tiers`, `messageOnCake`) and code from `CustomCake`, while adding its own unique properties. |
| **Polymorphism (Dynamic / Runtime)** | `CustomCake`<br>`BirthdayCake`<br>`WeddingCake`<br>`AnniversaryCake`<br>`GeneralCustomCake` | Method Overriding:<br>`calculatePrice()` and `getCakeType()` in all 4 subclasses | The service, repository, and controller handle references of type `CustomCake`. When `calculatePrice()` is invoked, the JVM dynamically resolves the concrete subclass method at runtime (dynamic method dispatch). |
| **Polymorphism (Dynamic / Runtime)** | `CakeBooking` | `public BigDecimal getPrice()` | **Dynamic Price Resolution:** If bakery management overrides or finalizes a price (`customPrice != null`), `getPrice()` returns it; otherwise it delegates polymorphically to `cake.calculatePrice()`. |
| **Polymorphism (Static / Compile-Time)** | `CakeBookingFactory`<br>`CakeBookingService`<br>`CakeBooking` | Method & Constructor Overloading:<br>`createFromForm(...)` vs `createFromFileLine(...)`<br>`createBooking(...)` (with/without custom price & image)<br>`adminUpdateBooking(...)`<br>`CakeBooking(...)` constructors (9, 8, and 4 parameters) | Multiple methods/constructors share the same name with different parameter signatures. The compiler binds the correct invocation at compile time based on argument count and types. |
| **Encapsulation & Business Integrity** | `CakeBooking`<br>`CakeBookingService` | Read-only `status`<br>`BookingStatus` | **Status is read-only for customers:** Customers can view (`PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`), but cannot modify status. When updating a booking, only cake details, date, and image can be changed. Status transitions and final price overrides are managed solely by bakery administration. |
| **Encapsulation** | `CustomCake`<br>`CakeBooking`<br>Subclasses | Private fields + Public getters & validated setters throwing `InvalidBookingException` | Protects object integrity. Fields cannot be set to invalid states (e.g., negative weight, zero tiers, negative custom price, blank venue, invalid phone format). Internal representation is hidden from callers. |
| **Information Hiding** | `FileCakeBookingRepository` | Private helper methods: `serialise()`, `deserialise()`, `escape()`, `unescape()`, `readRawLines()`, `writeLines()` | Text file serialization, delimiter escaping (`\pipe`, `\n`), backwards-compatible 15/16 field parsing, and file I/O operations are strictly encapsulated inside the repository. Outside classes never see raw pipe-delimited lines. |
| **Composition** | `CakeBooking` | `private CustomCake cake;` | A `CakeBooking` **"HAS-A"** `CustomCake`. Instead of inheriting from `CustomCake`, a booking wraps cake specifications with transaction metadata (customer ID, delivery date, booking status, image filename, custom price). |
| **Creational Design (Factory Pattern)** | `CakeBookingFactory` | `createFromForm(...)`<br>`createFromFileLine(...)` | Centralizes subclass instantiation logic. Controllers and repositories request cakes via factory methods without coupling to concrete subclass constructors directly. |

---

## 2. In-Depth Viva Explanations

### Abstraction
- **What is it?** Hiding complex implementation details and showing only the essential features to the outside world.
- **Where is it used?**
  1. `CustomCake` is an `abstract class`. You cannot instantiate it directly (`new CustomCake(...)`). Subclasses must provide their own concrete logic for `calculatePrice()` and `getCakeType()`.
  2. `BookingRepository` and `CurrentCustomerProvider` are `interfaces`. The service layer interacts only with interface contracts, complying with the **Dependency Inversion Principle** (depend on abstractions, not concretions).

### Inheritance
- **What is it?** A mechanism where a new class derives properties and characteristics from an existing class to promote code reuse and establish hierarchical relationships.
- **Where is it used?**
  - Subclasses `BirthdayCake`, `WeddingCake`, `AnniversaryCake`, and `GeneralCustomCake` extend `CustomCake`.
  - Common attributes (`flavour`, `weightKg`, `tiers`, `messageOnCake`, `specialInstructions`) and methods (`basePrice()`, `toString()`) are defined once in `CustomCake` and inherited by all four subclasses.
  - Subclass constructors invoke `super(...)` to enforce centralized base validation before initializing subclass-specific fields (`age`, `candles`, `floralDecoration`, `venueName`, `yearsOfAnniversary`, `occasionDescription`).

### Polymorphism
- **Runtime (Dynamic) Polymorphism / Method Overriding:**
  - `calculatePrice()` is declared in `CustomCake` and overridden in each subclass with specialized pricing rules:
    - `BirthdayCake`: Base price + Rs. 200 per candle.
    - `WeddingCake`: Base price × 1.5 multiplier + Rs. 2,000 for floral decoration.
    - `AnniversaryCake`: Base price × 1.2 multiplier + Rs. 500 per decade of marriage.
    - `GeneralCustomCake`: Pure base price without markup.
  - In `CakeBooking` and Thymeleaf views, `booking.getPrice()` seamlessly resolves either the admin's finalized price or invokes `cake.calculatePrice()` without inspecting subclass types.
- **Compile-time (Static) Polymorphism / Method Overloading:**
  - `CakeBookingFactory` overloads `createFromForm(...)` and `createFromFileLine(...)`.
  - `CakeBookingService` overloads `createBooking(...)` (with/without custom price and image) and `adminUpdateBooking(...)`.
  - `CakeBooking` overloads its constructors: 9 parameters (full with custom price), 8 parameters (legacy/calculated), and 4 parameters (new customer booking).

### Encapsulation
- **What is it?** Bundling data (fields) and methods operating on that data within a single class, while restricting direct access through access modifiers (`private`).
- **Where is it used?**
  - All fields in `CustomCake`, `CakeBooking`, and subclasses are `private`.
  - Public setters enforce strict boundary checks:
    - Weight must be strictly positive (`> 0`).
    - Tiers must be at least 1.
    - Delivery date must be at least 3 days into the future.
    - Phone number must match a valid Sri Lankan or international format regex (`^(07\d{8}|\+\d{7,15})$`).
    - Custom price cannot be negative.
    - Violations trigger an immediate `InvalidBookingException`, preventing inconsistent state.

### Information Hiding & Decoupling
- Text file formatting (`|` delimiters, `\pipe` and `\n` escaping) is entirely private to `FileCakeBookingRepository`.
- Customer identity and administrative privileges are retrieved through `CurrentCustomerProvider`, allowing Spring Security and User Management to evolve independently without breaking cake bookings.

