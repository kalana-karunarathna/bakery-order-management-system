package com.bakery.common.persistence;

/** Where User and Order Management keep their data. Selected with {@code bakery.storage.type}. */
public enum StorageType {
    /** UTF-8 TXT files in {@code bakery.data-directory} (the original implementation). */
    FILE,
    /** MongoDB, connection from {@code spring.data.mongodb.uri}. */
    MONGODB
}
