package com.bakery.user.repository.mongo;

import com.bakery.user.model.AccountStatus;
import com.bakery.user.model.CustomerType;
import com.bakery.user.model.User;
import com.bakery.user.model.UserRole;
import com.bakery.user.repository.UserFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * MongoDB shape of an account ({@code users} collection). Kept separate from the domain classes so
 * {@link User} and its subclasses stay free of persistence annotations and keep their validating
 * constructors.
 *
 * <p>Inheritance strategy: one collection, discriminated by {@code role} + {@code customerType}
 * (the same pair the TXT file uses); {@link UserFactory} rebuilds the matching subclass.</p>
 *
 * <p>{@code version} enables optimistic locking, so two concurrent updates cannot silently
 * overwrite each other. There is intentionally no {@code toString()} that could print the hash.</p>
 */
@Document(collection = MongoUserRepository.COLLECTION)
public class UserDocument {

    @Id
    private String id;
    private String role;
    private String customerType;
    private String fullName;
    /** Normalised (lower-case); unique index {@value MongoUserRepository#EMAIL_INDEX}. */
    private String email;
    private String phone;
    private String address;
    private String passwordHash;
    private String status;
    /** ISO date (yyyy-MM-dd), stored as text so it never shifts with time zones. */
    private String createdDate;
    @Version
    private Long version;

    protected UserDocument() {
        // for Spring Data
    }

    static UserDocument from(User user, String id, Long version) {
        UserDocument d = new UserDocument();
        d.id = id;
        d.role = user.getRole().name();
        d.customerType = user.getCustomerType().name();
        d.fullName = user.getFullName();
        d.email = user.getEmail();
        d.phone = user.getPhone();
        d.address = user.getAddress();
        d.passwordHash = user.getPasswordHash();
        d.status = user.getStatus().name();
        d.createdDate = user.getCreatedDate().toString();
        d.version = version;
        return d;
    }

    /**
     * Rebuilds the domain object; the domain constructors validate every field.
     *
     * @throws IllegalArgumentException if the stored document is invalid
     */
    User toDomain() {
        return UserFactory.create(id, UserRole.valueOf(role), CustomerType.valueOf(customerType), fullName, email,
                phone == null ? "" : phone, address == null ? "" : address, passwordHash,
                AccountStatus.valueOf(status), LocalDate.parse(createdDate));
    }

    String getId() { return id; }
    String getEmail() { return email; }
    Long getVersion() { return version; }
}
