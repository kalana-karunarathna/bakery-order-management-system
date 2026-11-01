# Custom Cake Booking Class Diagram

```mermaid
classDiagram
    class CustomCake {
        <<abstract>>
        -String flavour
        -double weightKg
        -int tiers
        -String messageOnCake
        -String specialInstructions
        +calculatePrice()* BigDecimal
        +getCakeType()* String
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

    CustomCake <|-- BirthdayCake
    CustomCake <|-- WeddingCake
```
