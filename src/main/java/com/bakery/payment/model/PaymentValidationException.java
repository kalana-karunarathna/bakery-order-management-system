package com.bakery.payment.model;

/** Associates a safe business error with a form field, without exposing internal details. */
public class PaymentValidationException extends IllegalArgumentException {
    private final String field;
    public PaymentValidationException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}