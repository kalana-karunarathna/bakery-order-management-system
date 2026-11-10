package com.bakery.common.persistence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.Locale;

/**
 * Checks {@code bakery.storage.type} before any bean is created, so a typo such as
 * {@code BAKERY_STORAGE=mongo} stops startup with a clear message instead of a confusing
 * "no bean of type UserRepository" error. Registered in META-INF/spring.factories.
 */
public class StorageTypeValidator implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String value = environment.getProperty("bakery.storage.type");
        if (value == null || value.isBlank()) {
            return; // the repositories fall back to TXT files
        }
        boolean known = Arrays.stream(StorageType.values())
                .anyMatch(type -> type.name().equals(value.trim().toUpperCase(Locale.ROOT)));
        if (!known) {
            throw new IllegalStateException("Invalid bakery.storage.type '" + value
                    + "': use 'mongodb' or 'file' (environment variable BAKERY_STORAGE).");
        }
    }
}
