package com.bakery.user.controller;

import com.bakery.user.service.UserValidationException;
import org.springframework.validation.BindingResult;

/** Copies service validation messages onto a Spring {@link BindingResult} for th:errors. */
final class FormErrors {

    private FormErrors() {
    }

    static void apply(UserValidationException e, BindingResult result) {
        e.getFieldErrors().forEach((field, message) -> {
            if (field == null) {
                result.reject("invalid", message);
            } else {
                result.rejectValue(field, "invalid", message);
            }
        });
    }
}
