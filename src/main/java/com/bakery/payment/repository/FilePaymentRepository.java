package com.bakery.payment.repository;

import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * TXT implementation ({@code payments.txt} in {@code bakery.data-directory}), active when
 * {@code bakery.storage.type=file}. Single-instance transaction boundary: atomic replacement prevents
 * partial file writes. (Originally {@code PaymentRepository}, by the Payment Management owner.)
 */
@Repository
@ConditionalOnProperty(name = "bakery.storage.type", havingValue = "file", matchIfMissing = true)
public class FilePaymentRepository implements PaymentRepository {
    private final Path file;
    private static final java.util.concurrent.ConcurrentMap<Path,Object> LOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    private final Object lock;
    private long sequence;
    /** Called only inside the transaction lock. Counter is persisted with the records. */
    @Override
    public String nextId() {
        if (!Thread.holdsLock(lock)) throw new IllegalStateException("ID allocation requires transaction");
        if (sequence == Long.MAX_VALUE) throw new IllegalArgumentException("Payment identifier limit reached.");
        return String.format(java.util.Locale.ROOT, "PAY%03d", ++sequence);
    }
    public FilePaymentRepository(@Value("${bakery.data-directory:data}") String directory) {
        file = Path.of(directory).toAbsolutePath().normalize().resolve("payments.txt");
        lock = LOCKS.computeIfAbsent(file, ignored -> new Object());
    }
    @Override
    public synchronized <T> T transaction(Function<List<Payment>, T> action, boolean write) {
        synchronized (lock) { try {
            List<Payment> records = read();
            T result = action.apply(records);
            if (write) save(records);
            return result;
        } catch (IOException | java.time.format.DateTimeParseException ex) {
            throw new StorageException("Payment storage unavailable. No success was recorded.", ex);
        } }
    }
    @Override
    public List<Payment> findByTarget(TargetType type, String targetId) {
        return transaction(list -> list.stream()
            .filter(p -> p.getTargetType() == type && p.getTargetId().equals(targetId)).toList(), false);
    }
    private List<Payment> read() throws IOException {
        sequence = 0;
        List<Payment> records = new ArrayList<>();
        if (!Files.exists(file)) return records;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith("﻿")) line = line.substring(1);
            if (line.startsWith("# sequence=")) {
                try {
                    long stored = Long.parseLong(line.substring(11));
                    if (stored < 0) throw new NumberFormatException();
                    sequence = Math.max(sequence, stored);
                }
                catch (NumberFormatException ex) { throw new IOException("Invalid payment sequence", ex); }
            }
            if (line.isBlank() || line.startsWith("#")) continue;
            if (line.startsWith("PAY001|")) sequence = Math.max(sequence, 1);
            String[] f = line.split("\\|", -1);
            // The scaffold's six-column fictional sample is retained verbatim on save.
            if (f.length == 6 && line.equals(SCAFFOLD_SAMPLE)) continue;
            try {
                if (f.length != 13 || !f[0].equals("v1")) throw new IllegalArgumentException();
                records.add(Payment.create(Method.valueOf(f[7]), f[1], TargetType.valueOf(f[2]), f[3],
                    f[4], new BigDecimal(f[5]), new BigDecimal(f[6]), Purpose.valueOf(f[8]),
                    Status.valueOf(f[9]), f[10], f[11], Instant.parse(f[12])));
            } catch (RuntimeException ex) {
                throw new IOException("Malformed payment record; refusing to overwrite file", ex);
            }
        }
        if (records.stream().map(Payment::getId).distinct().count() != records.size())
            throw new IOException("Duplicate payment identifiers");
        for (Payment p : records) {
            if (p.getId().matches("PAY[0-9]+")) {
                try { sequence = Math.max(sequence, Long.parseLong(p.getId().substring(3))); }
                catch (NumberFormatException ex) { throw new IOException("Invalid payment identifier",ex); }
            }
        }
        PaymentRecords.validate(records);
        return records;
    }
    private static final String SCAFFOLD_SAMPLE = "PAY001|O001|3000.00|CASH|PENDING|INV001";
    private void save(List<Payment> records) throws IOException {
        PaymentRecords.validate(records);
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("# sequence=" + sequence);
        lines.add("# v1|id|targetType|targetId|customerId|total|amount|method|purpose|status|reference|requestKey|createdAt");
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
                if (line.equals(SCAFFOLD_SAMPLE)) {
                    lines.add("# Original fictional scaffold example (not a live payment)");
                    lines.add(line);
                }
        }
        for (Payment p : records) lines.add(String.join("|", "v1", p.getId(), p.getTargetType().name(),
            p.getTargetId(), p.getCustomerId(), p.getTotal().toPlainString(), p.getAmount().toPlainString(),
            p.getMethod().name(), p.getPurpose().name(), p.getStatus().name(), p.getReference(),
            p.getRequestKey(), p.getCreatedAt().toString()));
        Path temporary = Files.createTempFile(file.getParent(), "payments-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
