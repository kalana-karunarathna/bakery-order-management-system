# OOP Notes: Custom Cake Booking Module (`com.bakery.cake`)

This document maps each Object-Oriented Programming (OOP) concept required by the university specification directly to the classes, methods, and design structures in `com.bakery.cake`.

---

## 1. Quick Mapping Table

| OOP Concept | Class / Interface | Method / Field / Construct | Viva Explanation |
| :--- | :--- | :--- | :--- |
| **Abstraction** | `CustomCake` | `public abstract BigDecimal calculatePrice();`<br>`public abstract String getCakeType();` | We cannot instantiate a generic "custom cake" directly because price and type name depend on the specific cake category. An abstract class defines *what* behaviors must exist without prescribing *how* each category calculates them. |
| **Inheritance** | `BirthdayCake`<br>`WeddingCake`<br>`AnniversaryCake`<br>`GeneralCustomCake` | `extends CustomCake`<br>`super(flavour, weightKg, tiers, ...)` | Establishes a true **"IS-A"** relationship. Each specific cake inherits common attributes (`flavour`, `weightKg`, `tiers`, `messageOnCake`) and code from `CustomCake`, while adding its own unique properties. |
| **Polymorphism (Dynamic / Runtime)** | `CustomCake`<br>`BirthdayCake`<br>`WeddingCake`<br>`AnniversaryCake`<br>`GeneralCustomCake` | Method Overriding:<br>`calculatePrice()` and `getCakeType()` in all 4 subclasses | The service, repository, and controller handle references of type `CustomCake`. When `calculatePrice()` is invoked, the JVM dynamically resolves the concrete subclass method at runtime (dynamic method dispatch). |
| **Polymorphism (Dynamic / Runtime)** | `CakeBooking` | `public BigDecimal getPrice()` | **Dynamic Price Resolution:** If bakery management overrides or finalizes a price (`customPrice != null`), `getPrice()` returns it; otherwise it delegates polymorphically to `cake.calculatePrice()`. |
| **Encapsulation** | `CustomCake`<br>`CakeBooking`<br>Subclasses | Private fields + Public getters & validated setters throwing `InvalidBookingException` | Protects object integrity. Fields cannot be set to invalid states (e.g., negative weight, zero tiers, negative custom price, blank venue, invalid phone format). Internal representation is hidden from callers. |
| **Composition** | `CakeBooking` | `private CustomCake cake;` | A `CakeBooking` **"HAS-A"** `CustomCake`. Instead of inheriting from `CustomCake`, a booking wraps cake specifications with transaction metadata (customer ID, delivery date, booking status, image filename, custom price). |
