package com.bakery.common.persistence;

import com.bakery.BakeryApplication;
import com.bakery.order.repository.FileOrderRepository;
import com.bakery.order.repository.OrderRepository;
import com.bakery.user.repository.FileUserRepository;
import com.bakery.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code bakery.storage.type} selects exactly one implementation and rejects unknown values. */
@SpringBootTest
class StorageModeSelectionTests {

    @Autowired UserRepository userRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired StorageProperties storage;
    @Autowired ApplicationContext context;

    @Test
    void fileModeUsesOnlyTheTxtRepositories() {
        assertEquals(StorageType.FILE, storage.type());
        assertInstanceOf(FileUserRepository.class, userRepository);
        assertInstanceOf(FileOrderRepository.class, orderRepository);
        assertEquals(1, context.getBeansOfType(UserRepository.class).size());
        assertEquals(1, context.getBeansOfType(OrderRepository.class).size());
        assertEquals(0, context.getBeansOfType(MongoSequenceGenerator.class).size());
    }

    @Test
    void unknownStorageTypeStopsStartupWithAClearMessage() {
        Exception e = assertThrows(Exception.class, () -> new SpringApplicationBuilder(BakeryApplication.class)
                .web(WebApplicationType.NONE)
                // command-line arguments outrank the test configuration (bakery.storage.type=file)
                .run("--bakery.storage.type=cassandra",
                        "--spring.data.mongodb.uri=mongodb://127.0.0.1:27017/bakery_test_unused").close());
        Throwable root = e;
        StringBuilder messages = new StringBuilder();
        while (root != null) {
            messages.append(root.getMessage()).append('\n');
            root = root.getCause();
        }
        assertTrue(messages.toString().contains("bakery.storage"), messages.toString());
    }
}
