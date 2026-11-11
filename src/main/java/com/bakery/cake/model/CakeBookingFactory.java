package com.bakery.cake.model;

import com.bakery.cake.exception.InvalidBookingException;

/**
 * OOP: Factory Pattern (Creational) + Information Hiding.
 * Centralizes the creation of CustomCake subclasses.
 * Callers receive a CustomCake reference without needing to know
 * which subclass constructor was called.
 */
public class CakeBookingFactory {

    public static final String TYPE_BIRTHDAY    = "BIRTHDAY";
    public static final String TYPE_WEDDING     = "WEDDING";
    public static final String TYPE_ANNIVERSARY = "ANNIVERSARY";
    public static final String TYPE_GENERAL     = "GENERAL";

    // OOP: Factory method – creates the right CustomCake subclass from form inputs
    public static CustomCake createFromForm(String cakeType,
                                            String flavour,
                                            double weightKg,
                                            int tiers,
                                            String messageOnCake,
                                            String specialInstructions,
                                            String extra1,
                                            String extra2) {
        String type = cakeType != null ? cakeType.trim().toUpperCase() : "";

        switch (type) {
            case TYPE_BIRTHDAY:
                int age = parseIntSafe(extra1, "age");
                int candles = parseIntSafe(extra2, "candles");
                return new BirthdayCake(flavour, weightKg, tiers, messageOnCake, specialInstructions, age, candles);

            case TYPE_WEDDING:
                boolean floral = parseBooleanSafe(extra1);
                String venue = extractCleanString(extra2);
                return new WeddingCake(flavour, weightKg, tiers, messageOnCake, specialInstructions, floral, venue);

            case TYPE_ANNIVERSARY:
                int years = parseIntSafe(extra1, "yearsOfAnniversary");
                String quote = extractCleanString(extra2);
                return new AnniversaryCake(flavour, weightKg, tiers, messageOnCake, specialInstructions, years, quote);

            case TYPE_GENERAL:
                String occasion = extractCleanString(extra1);
                return new GeneralCustomCake(flavour, weightKg, tiers, messageOnCake, specialInstructions, occasion);

            default:
                throw new InvalidBookingException("cakeType", "Unknown cake type: " + cakeType);
        }
    }

    // OOP: Polymorphism (Method Overloading) – creates CustomCake from file values
    public static CustomCake createFromFileLine(String cakeType,
                                                String flavour,
                                                String weightKg,
                                                String tiers,
                                                String message,
                                                String special,
                                                String extra1,
                                                String extra2) {
        double weight = parseDoubleSafe(weightKg, "weightKg");
        int tierCount = parseIntSafe(tiers, "tiers");
        return createFromForm(cakeType, flavour, weight, tierCount, message, special, extra1, extra2);
    }

    private static int parseIntSafe(String val, String field) {
        if (val == null || val.isBlank()) {
            return 0;
        }
        // If multiple HTML inputs with name="extra1" were submitted, extract the valid number token
        if (val.contains(",")) {
            for (String part : val.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        return Integer.parseInt(trimmed);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            throw new InvalidBookingException(field, field + " must be a valid number.");
        }
    }

    private static double parseDoubleSafe(String val, String field) {
        if (val == null || val.isBlank()) {
            return 0.0;
        }
        if (val.contains(",")) {
            for (String part : val.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        return Double.parseDouble(trimmed);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            throw new InvalidBookingException(field, field + " must be a valid number.");
        }
    }

    private static boolean parseBooleanSafe(String val) {
        if (val == null) {
            return false;
        }
        if (val.contains(",")) {
            for (String part : val.split(",")) {
                String trimmed = part.trim();
                if ("true".equalsIgnoreCase(trimmed)) return true;
                if ("false".equalsIgnoreCase(trimmed)) return false;
            }
        }
        return Boolean.parseBoolean(val.trim());
    }

    private static String extractCleanString(String val) {
        if (val == null) {
            return "";
        }
        if (val.contains(",")) {
            for (String part : val.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !"false".equalsIgnoreCase(trimmed) && !"true".equalsIgnoreCase(trimmed)) {
                    return trimmed;
                }
            }
            // Fallback to first non-empty part
            for (String part : val.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) return trimmed;
            }
            return "";
        }
        return val.trim();
    }
}
