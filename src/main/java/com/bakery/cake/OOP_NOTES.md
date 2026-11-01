# OOP Notes: Custom Cake Booking Module (`com.bakery.cake`)

This document maps each Object-Oriented Programming (OOP) concept required by the university specification directly to the classes, methods, and design structures in `com.bakery.cake`.

---

## 1. Quick Mapping Table

| OOP Concept | Class / Interface | Method / Field / Construct | Viva Explanation |
| :--- | :--- | :--- | :--- |
| **Abstraction** | `CustomCake` | `public abstract BigDecimal calculatePrice();`<br>`public abstract String getCakeType();` | We cannot instantiate a generic "custom cake" directly because price and type name depend on the specific cake category. An abstract class defines *what* behaviors must exist without prescribing *how* each category calculates them. |
| **Inheritance** | `BirthdayCake`<br>`WeddingCake`<br>`AnniversaryCake`<br>`GeneralCustomCake` | `extends CustomCake`<br>`super(flavour, weightKg, tiers, ...)` | Establishes a true **"IS-A"** relationship. Each specific cake inherits common attributes (`flavour`, `weightKg`, `tiers`, `messageOnCake`) and code from `CustomCake`, while adding its own unique properties. |
