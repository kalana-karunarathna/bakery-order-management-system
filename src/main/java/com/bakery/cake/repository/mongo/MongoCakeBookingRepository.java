package com.bakery.cake.repository.mongo;

import com.bakery.cake.model.CakeBooking;
import com.bakery.cake.model.CakeBookingFactory;
import com.bakery.cake.model.CustomCake;
import com.bakery.cake.model.BookingStatus;
import com.bakery.cake.repository.BookingRepository;
import com.bakery.cake.repository.FileCakeBookingRepository;
import com.bakery.common.TextFileStorage;
import com.bakery.common.persistence.MongoCollections;
import com.bakery.common.persistence.MongoSequenceGenerator;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB implementation of {@link BookingRepository} ({@code cake_bookings} collection).
 * Stores all custom cake bookings in MongoDB when {@code bakery.storage.type=mongodb}.
 */
@Repository
@ConditionalOnProperty(name = "bakery.storage.type", havingValue = "mongodb")
public class MongoCakeBookingRepository implements BookingRepository {

    public static final String COLLECTION = "cake_bookings";
    public static final String SEQUENCE = "cake_bookings";

    private static final Logger log = LoggerFactory.getLogger(MongoCakeBookingRepository.class);

    private final CakeBookingDocumentRepository documents;
    private final MongoTemplate template;
    private final MongoSequenceGenerator sequences;
    private final TextFileStorage storage;

    public MongoCakeBookingRepository(CakeBookingDocumentRepository documents,
                                      MongoTemplate template,
                                      MongoSequenceGenerator sequences,
                                      TextFileStorage storage) {
        this.documents = documents;
        this.template = template;
        this.sequences = sequences;
        this.storage = storage;
    }

    @PostConstruct
    void init() {
        // Ensure collection indexes
        try {
            if (!template.collectionExists(COLLECTION)) {
                template.createCollection(COLLECTION);
            }
            template.indexOps(COLLECTION).ensureIndex(new Index().on("customerId", Sort.Direction.ASC));
            template.indexOps(COLLECTION).ensureIndex(new Index().on("deliveryDate", Sort.Direction.ASC));
            template.indexOps(COLLECTION).ensureIndex(new Index().on("status", Sort.Direction.ASC));
            template.indexOps(COLLECTION).ensureIndex(new Index().on("createdAt", Sort.Direction.DESC));
        } catch (Exception e) {
            log.warn("Could not ensure indexes for collection '{}': {}", COLLECTION, e.getMessage());
        }

        // Auto-seed existing txt file bookings if MongoDB collection is empty
        try {
            long count = documents.count();
            if (count == 0) {
                log.info("MongoDB collection '{}' is empty. Migrating existing text file bookings...", COLLECTION);
                FileCakeBookingRepository fileRepo = new FileCakeBookingRepository(storage);
                List<CakeBooking> existing = fileRepo.findAll();
                for (CakeBooking b : existing) {
                    CakeBookingDocument doc = CakeBookingDocument.from(b, b.getBookingId(), null);
                    documents.save(doc);
                }
                log.info("Migrated {} cake bookings from text file to MongoDB collection '{}'.", existing.size(), COLLECTION);
            }
        } catch (Exception e) {
            log.warn("Auto-migration of cake bookings skipped: {}", e.getMessage());
        }

        // Synchronize sequence generator with highest existing ID
        long max = MongoCollections.maxIdNumber(template, COLLECTION, "CB");
        sequences.ensureAtLeast(SEQUENCE, max);
        log.info("MongoCakeBookingRepository initialized. Current max sequence: CB{:03d}", max);
    }

    @Override
    public List<CakeBooking> findAll() {
        List<CakeBooking> list = new ArrayList<>();
        for (CakeBookingDocument doc : documents.findAllByOrderByCreatedAtDesc()) {
            try {
                list.add(doc.toDomain());
            } catch (Exception e) {
                log.error("Failed to parse cake booking document {}: {}", doc.getId(), e.getMessage());
            }
        }
        return list;
    }

    @Override
    public Optional<CakeBooking> findById(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return Optional.empty();
        }
        return documents.findById(bookingId).map(CakeBookingDocument::toDomain);
    }

    @Override
    public List<CakeBooking> findByCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return List.of();
        }
        List<CakeBooking> list = new ArrayList<>();
        for (CakeBookingDocument doc : documents.findByCustomerIdOrderByCreatedAtDesc(customerId)) {
            try {
                list.add(doc.toDomain());
            } catch (Exception e) {
                log.error("Failed to parse cake booking document {}: {}", doc.getId(), e.getMessage());
            }
        }
        return list;
    }

    @Override
    public void save(CakeBooking booking) {
        String newId = generateNextId();
        booking.setBookingId(newId);
        CakeBookingDocument doc = CakeBookingDocument.from(booking, newId, null);
        documents.save(doc);
        log.info("Saved cake booking {} for customer {} to MongoDB.", newId, booking.getCustomerId());
    }

    @Override
    public void update(CakeBooking booking) {
        if (booking.getBookingId() == null) {
            throw new IllegalArgumentException("Cannot update booking without bookingId");
        }
        Optional<CakeBookingDocument> existingOpt = documents.findById(booking.getBookingId());
        Long version = existingOpt.map(CakeBookingDocument::getVersion).orElse(null);
        CakeBookingDocument doc = CakeBookingDocument.from(booking, booking.getBookingId(), version);
        documents.save(doc);
        log.info("Updated cake booking {} in MongoDB.", booking.getBookingId());
    }

    @Override
    public void delete(String bookingId) {
        if (bookingId != null) {
            documents.deleteById(bookingId);
            log.info("Deleted cake booking {} from MongoDB.", bookingId);
        }
    }

    @Override
    public String generateNextId() {
        long next = sequences.next(SEQUENCE);
        return String.format("CB%03d", next);
    }
}

