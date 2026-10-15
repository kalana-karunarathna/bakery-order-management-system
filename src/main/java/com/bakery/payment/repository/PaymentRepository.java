package com.bakery.payment.repository;

import com.bakery.payment.model.Payment;
import com.bakery.payment.model.Payment.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Single-instance transaction boundary: atomic replacement prevents partial file writes. */
@Repository
public class PaymentRepository {
    private final Path file;
    private static final java.util.concurrent.ConcurrentMap<Path,Object> LOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    private final Object lock;
    private long sequence;
    /** Called only inside the transaction lock. Counter is persisted with the records. */
    public String nextId() {
        if (!Thread.holdsLock(lock)) throw new IllegalStateException("ID allocation requires transaction");
        if (sequence == Long.MAX_VALUE) throw new IllegalArgumentException("Payment identifier limit reached.");
        return String.format(java.util.Locale.ROOT, "PAY%03d", ++sequence);
    }
    public PaymentRepository(@Value("${bakery.data-directory:data}") String directory) {
        file = Path.of(directory).toAbsolutePath().normalize().resolve("payments.txt");
        lock = LOCKS.computeIfAbsent(file, ignored -> new Object());
    }
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
    private List<Payment> read() throws IOException {
        sequence = 0;
        List<Payment> records = new ArrayList<>();
        if (!Files.exists(file)) return records;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
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
            if (f.length == 6 && line.equals("PAY001|O001|3000.00|CASH|PENDING|INV001")) continue;
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
        validateRecords(records);
        return records;
    }
    private void save(List<Payment> records) throws IOException {
        validateRecords(records);
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("# sequence=" + sequence);
        lines.add("# v1|id|targetType|targetId|customerId|total|amount|method|purpose|status|reference|requestKey|createdAt");
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
                if (line.equals("PAY001|O001|3000.00|CASH|PENDING|INV001")) {
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
    /** Fail closed on corrupted data; never discard a bad line and overwrite good records. */
    private void validateRecords(List<Payment> records) throws IOException {
        Set<String> ids = new HashSet<>(), keys = new HashSet<>(), references = new HashSet<>();
        Map<String,BigDecimal> reserved = new HashMap<>(), totals = new HashMap<>();
        Map<String,String> owners = new HashMap<>();
        for (Payment p : records) {
            if (!ids.add(p.getId()) || !keys.add(p.getRequestKey()) ||
                    !references.add(p.getReference().toUpperCase(Locale.ROOT)))
                throw new IOException("Duplicate payment record");
            String target = p.getTargetType() + ":" + p.getTargetId();
            if (totals.containsKey(target) && (totals.get(target).compareTo(p.getTotal()) != 0 ||
                    !owners.get(target).equals(p.getCustomerId())))
                throw new IOException("Conflicting target details");
            totals.put(target,p.getTotal()); owners.put(target,p.getCustomerId());
            if (p.getPurpose() == Purpose.ADVANCE && (p.getTargetType() != TargetType.BOOKING ||
                    p.getAmount().compareTo(p.getTotal()) >= 0))
                throw new IOException("Invalid advance record");
            if (p.getStatus() == Status.PENDING || p.getStatus() == Status.PAID)
                reserved.merge(target,p.getAmount(),BigDecimal::add);
        }
        for (var entry : reserved.entrySet())
            if (entry.getValue().compareTo(totals.get(entry.getKey())) > 0)
                throw new IOException("Payment totals exceed agreed total");
    }
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) { super(message, cause); }
    }
}