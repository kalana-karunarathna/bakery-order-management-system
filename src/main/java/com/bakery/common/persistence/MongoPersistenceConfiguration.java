package com.bakery.common.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.concurrent.TimeUnit;

/** MongoDB-only beans. Loaded only when {@code bakery.storage.type=mongodb}. */
@Configuration
@ConditionalOnProperty(name = "bakery.storage.type", havingValue = "mongodb")
public class MongoPersistenceConfiguration {

    /** Fail fast (5 s instead of the driver's 30 s) when the local MongoDB service is not running. */
    @Bean
    public MongoClientSettingsBuilderCustomizer bakeryMongoTimeouts() {
        return builder -> builder
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout(5, TimeUnit.SECONDS));
    }

    @Bean
    public MongoSequenceGenerator mongoSequenceGenerator(MongoTemplate mongoTemplate) {
        return new MongoSequenceGenerator(mongoTemplate);
    }
}
