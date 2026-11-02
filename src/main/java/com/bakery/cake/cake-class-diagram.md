# Custom Cake Booking Class Diagram

```mermaid
classDiagram
    class CustomCake {
        <<abstract>>
        #BigDecimal BASE_PRICE_PER_KG
        #BigDecimal PRICE_PER_EXTRA_TIER
        -String flavour
        -double weightKg
        -int tiers
        -String messageOnCake
        -String specialInstructions
        +calculatePrice()* BigDecimal
        +getCakeType()* String
        +basePrice() BigDecimal
    }

    class BirthdayCake {
        -int age
        -int candles
        +calculatePrice() BigDecimal
        +getCakeType() String
    }

    class WeddingCake {
        -boolean floralDecoration
        -String venueName
        +calculatePrice() BigDecimal
        +getCakeType() String
    }

    class AnniversaryCake {
        -int yearsOfAnniversary
        -String dedicationQuote
        +calculatePrice() BigDecimal
        +getCakeType() String
    }

    class GeneralCustomCake {
        -String occasionDescription
        +calculatePrice() BigDecimal
        +getCakeType() String
    }

    class CakeBooking {
        -String bookingId
        -String customerId
        -CustomCake cake
        -LocalDate deliveryDate
        -String contactPhone
        -BookingStatus status
        -String imageFileName
        -LocalDateTime createdAt
        -BigDecimal customPrice
        +getPrice() BigDecimal
    }

    CustomCake <|-- BirthdayCake
    CustomCake <|-- WeddingCake
    CustomCake <|-- AnniversaryCake
    CustomCake <|-- GeneralCustomCake

    CakeBooking *-- CustomCake : Composition (HAS-A)
    CakeBooking o-- BookingStatus : Association
```
