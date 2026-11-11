package com.bakery.cake.repository.mongo;

import com.bakery.cake.model.AnniversaryCake;
import com.bakery.cake.model.BirthdayCake;
import com.bakery.cake.model.BookingStatus;
import com.bakery.cake.model.CakeBooking;
import com.bakery.cake.model.CakeBookingFactory;
import com.bakery.cake.model.CustomCake;
import com.bakery.cake.model.GeneralCustomCake;
import com.bakery.cake.model.WeddingCake;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MongoDB document representation for cake bookings ({@code cake_bookings} collection).
 * Decoupled from the domain layer to keep {@link CakeBooking} free of database annotations.
 */
@Document(collection = MongoCakeBookingRepository.COLLECTION)
public class CakeBookingDocument {

    @Id
    private String id;
    private String customerId;
    private String cakeType;
    private String flavour;
    private double weightKg;
    private int tiers;
    private String messageOnCake;
    private String specialInstructions;
    private String extra1;
    private String extra2;
    private String deliveryDate;
    private String contactPhone;
    private String status;
    private String imageFileName;
    private String createdAt;
    private BigDecimal customPrice;

    @Version
    private Long version;

    public CakeBookingDocument() {
        // Required for Spring Data MongoDB
    }

    public static CakeBookingDocument from(CakeBooking b, String id, Long version) {
        CustomCake cake = b.getCake();
        String[] extras = extractExtras(cake);

        CakeBookingDocument doc = new CakeBookingDocument();
        doc.id = id != null ? id : b.getBookingId();
        doc.customerId = b.getCustomerId();
        doc.cakeType = cakeTypeToken(cake);
        doc.flavour = cake.getFlavour();
        doc.weightKg = cake.getWeightKg();
        doc.tiers = cake.getTiers();
        doc.messageOnCake = cake.getMessageOnCake();
        doc.specialInstructions = cake.getSpecialInstructions();
        doc.extra1 = extras[0];
        doc.extra2 = extras[1];
        doc.deliveryDate = b.getDeliveryDate().toString();
        doc.contactPhone = b.getContactPhone();
        doc.status = b.getStatus().name();
        doc.imageFileName = b.getImageFileName();
        doc.createdAt = b.getCreatedAt().toString();
        doc.customPrice = b.getCustomPrice();
        doc.version = version;
        return doc;
    }

    public CakeBooking toDomain() {
        CustomCake cake = CakeBookingFactory.createFromForm(
                cakeType, flavour, weightKg, tiers, messageOnCake, specialInstructions, extra1, extra2);

        LocalDate parsedDeliveryDate = deliveryDate != null ? LocalDate.parse(deliveryDate) : LocalDate.now().plusDays(3);
        BookingStatus parsedStatus = status != null ? BookingStatus.valueOf(status) : BookingStatus.PENDING;
        LocalDateTime parsedCreated = createdAt != null ? LocalDateTime.parse(createdAt) : LocalDateTime.now();

        return new CakeBooking(
                id,
                customerId,
                cake,
                parsedDeliveryDate,
                contactPhone,
                parsedStatus,
                imageFileName,
                parsedCreated,
                customPrice
        );
    }

    private static String cakeTypeToken(CustomCake cake) {
        if (cake instanceof BirthdayCake) return CakeBookingFactory.TYPE_BIRTHDAY;
        if (cake instanceof WeddingCake) return CakeBookingFactory.TYPE_WEDDING;
        if (cake instanceof AnniversaryCake) return CakeBookingFactory.TYPE_ANNIVERSARY;
        return CakeBookingFactory.TYPE_GENERAL;
    }

    private static String[] extractExtras(CustomCake cake) {
        if (cake instanceof BirthdayCake bc) {
            return new String[]{ String.valueOf(bc.getAge()), String.valueOf(bc.getCandles()) };
        } else if (cake instanceof WeddingCake wc) {
            return new String[]{ String.valueOf(wc.isFloralDecoration()), wc.getVenueName() };
        } else if (cake instanceof AnniversaryCake ac) {
            return new String[]{ String.valueOf(ac.getYearsOfAnniversary()), ac.getDedicationQuote() };
        } else if (cake instanceof GeneralCustomCake gc) {
            return new String[]{ gc.getOccasionDescription(), "" };
        }
        return new String[]{ "", "" };
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCakeType() { return cakeType; }
    public void setCakeType(String cakeType) { this.cakeType = cakeType; }

    public String getFlavour() { return flavour; }
    public void setFlavour(String flavour) { this.flavour = flavour; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public int getTiers() { return tiers; }
    public void setTiers(int tiers) { this.tiers = tiers; }

    public String getMessageOnCake() { return messageOnCake; }
    public void setMessageOnCake(String messageOnCake) { this.messageOnCake = messageOnCake; }

    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }

    public String getExtra1() { return extra1; }
    public void setExtra1(String extra1) { this.extra1 = extra1; }

    public String getExtra2() { return extra2; }
    public void setExtra2(String extra2) { this.extra2 = extra2; }

    public String getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(String deliveryDate) { this.deliveryDate = deliveryDate; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getImageFileName() { return imageFileName; }
    public void setImageFileName(String imageFileName) { this.imageFileName = imageFileName; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public BigDecimal getCustomPrice() { return customPrice; }
    public void setCustomPrice(BigDecimal customPrice) { this.customPrice = customPrice; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

