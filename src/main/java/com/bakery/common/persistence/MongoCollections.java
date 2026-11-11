package com.bakery.common.persistence;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.validation.Validator;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Idempotent collection set-up used at startup by the MongoDB repositories: creates the collection
 * with a {@code $jsonSchema} validator (or applies it to an existing collection with {@code collMod})
 * and ensures the indexes. Safe to run on every start; it never deletes data.
 */
public final class MongoCollections {

    private static final Logger log = LoggerFactory.getLogger(MongoCollections.class);
    private static final Pattern ID_NUMBER = Pattern.compile("^[A-Z](\\d+)$");

    private MongoCollections() {
    }

    public static void ensureCollection(MongoTemplate template, String collection, Document jsonSchema,
                                        List<Index> indexes) {
        if (!template.collectionExists(collection)) {
            template.createCollection(collection, CollectionOptions.empty()
                    .validator(Validator.document(new Document("$jsonSchema", jsonSchema)))
                    .strictValidation().failOnValidationError());
        } else {
            try {
                template.executeCommand(new Document("collMod", collection)
                        .append("validator", new Document("$jsonSchema", jsonSchema))
                        .append("validationLevel", "strict")
                        .append("validationAction", "error"));
            } catch (DataAccessException e) {
                // e.g. missing privileges on a managed cluster: the application still validates every record.
                log.warn("Could not apply the schema validator to '{}'; continuing with application-level validation.",
                        collection);
            }
        }
        for (Index index : indexes) {
            template.indexOps(collection).ensureIndex(index);
        }
    }

    /** Highest numeric part of IDs like U007 / O012 in a collection (0 if empty). */
    public static long maxIdNumber(MongoTemplate template, String collection) {
        return maxIdNumber(template, collection, ID_NUMBER);
    }

    /** Highest numeric part of IDs with a longer prefix, e.g. {@code "PAY"} for PAY007 (0 if empty). */
    public static long maxIdNumber(MongoTemplate template, String collection, String prefix) {
        return maxIdNumber(template, collection, Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$"));
    }

    private static long maxIdNumber(MongoTemplate template, String collection, Pattern idNumber) {
        long max = 0;
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.fields().include("_id");
        for (Document doc : template.find(query, Document.class, collection)) {
            Object id = doc.get("_id");
            if (id instanceof String s) {
                Matcher m = idNumber.matcher(s);
                if (m.matches()) max = Math.max(max, Long.parseLong(m.group(1)));
            }
        }
        return max;
    }

    public static Document string(String pattern) {
        Document d = new Document("bsonType", "string");
        if (pattern != null) d.append("pattern", pattern);
        return d;
    }

    public static Document enumOf(String... values) {
        return new Document("enum", List.of(values));
    }
}
