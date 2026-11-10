package com.bakery.common.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bakery.storage.type=mongodb|file}. Binding to the enum means an unknown value stops the
 * application at startup with a clear "failed to bind" message instead of a missing-bean error.
 *
 * <p>The repository beans themselves are chosen with {@code @ConditionalOnProperty} on the same
 * property (case-insensitive), so exactly one User and one Order repository exists.</p>
 */
@ConfigurationProperties(prefix = "bakery.storage")
public record StorageProperties(StorageType type) {

    public StorageProperties {
        if (type == null) {
            type = StorageType.FILE;
        }
    }
}
