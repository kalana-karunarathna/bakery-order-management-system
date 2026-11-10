package com.bakery.common.persistence;

import com.mongodb.ConnectionString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Always active. Validates {@code bakery.storage.type} and logs which store is in use
 * (without any credentials from the MongoDB URI).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class PersistenceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PersistenceConfiguration.class);

    private final StorageProperties storage;
    private final String mongoUri;
    private final String dataDirectory;

    public PersistenceConfiguration(StorageProperties storage,
                                    @Value("${spring.data.mongodb.uri:}") String mongoUri,
                                    @Value("${bakery.data-directory:data}") String dataDirectory) {
        this.storage = storage;
        this.mongoUri = mongoUri;
        this.dataDirectory = dataDirectory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStorageMode() {
        if (storage.type() == StorageType.MONGODB) {
            log.info("User and Order storage: MongoDB ({})", describe(mongoUri));
        } else {
            log.info("User and Order storage: TXT files in '{}'", dataDirectory);
        }
    }

    /** Host(s) and database only; user name and password are never logged. */
    static String describe(String uri) {
        try {
            ConnectionString cs = new ConnectionString(uri);
            return String.join(",", cs.getHosts()) + "/" + cs.getDatabase();
        } catch (RuntimeException e) {
            return "invalid URI";
        }
    }
}
