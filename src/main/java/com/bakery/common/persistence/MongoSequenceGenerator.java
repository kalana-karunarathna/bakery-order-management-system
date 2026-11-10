package com.bakery.common.persistence;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Sequential IDs (U001, O001, ...) for MongoDB, stored in the {@code counters} collection:
 * {@code { _id: "users", seq: 5 }}.
 *
 * <p>{@link #next} uses a single atomic {@code findAndModify $inc}, so concurrent requests can never
 * receive the same number, and numbers are never reused (deleted records keep theirs).
 * {@link #ensureAtLeast} uses {@code $max} so the counter can catch up with imported records but
 * never moves backwards.</p>
 */
public class MongoSequenceGenerator {

    public static final String COLLECTION = "counters";

    private final MongoTemplate template;

    public MongoSequenceGenerator(MongoTemplate template) {
        this.template = template;
    }

    /** Atomically increments and returns the counter (starting at 1). */
    public long next(String name) {
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
        for (int attempt = 1; ; attempt++) {
            try {
                Document counter = template.findAndModify(byName(name), new Update().inc("seq", 1L),
                        options, Document.class, COLLECTION);
                return ((Number) counter.get("seq")).longValue();
            } catch (DuplicateKeyException e) {
                // Two first-ever upserts raced to create the counter; the loser simply retries.
                if (attempt >= 3) throw e;
            }
        }
    }

    /** Raises the counter to at least {@code value} (never lowers it). */
    public void ensureAtLeast(String name, long value) {
        try {
            template.upsert(byName(name), new Update().max("seq", value), COLLECTION);
        } catch (DuplicateKeyException e) {
            template.upsert(byName(name), new Update().max("seq", value), COLLECTION);
        }
    }

    private static Query byName(String name) {
        return Query.query(Criteria.where("_id").is(name));
    }
}
