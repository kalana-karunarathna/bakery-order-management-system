package com.bakery.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextFileStorageTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesAndReadsUtf8AndReplacesPreviousContents() throws IOException {
        TextFileStorage storage = new TextFileStorage(temporaryDirectory.resolve("data").toString());
        List<String> lines = List.of("# id|name", "P001|Caf\u00e9 cake");
        storage.writeLines("products.txt", lines);
        assertEquals(lines, storage.readLines("products.txt"));

        storage.writeLines("products.txt", List.of("P002|Bread"));
        assertEquals(List.of("P002|Bread"), storage.readLines("products.txt"));
    }

    @Test
    void missingFileIsReportedWithoutCreatingIt() {
        TextFileStorage storage = new TextFileStorage(temporaryDirectory.toString());
        assertThrows(NoSuchFileException.class, () -> storage.readLines("missing.txt"));
        assertFalse(Files.exists(temporaryDirectory.resolve("missing.txt")));
    }

    @Test
    void rejectsPathsOutsideTheDataDirectory() {
        TextFileStorage storage = new TextFileStorage(temporaryDirectory.toString());
        for (String invalidName : List.of("../users.txt", "..\\users.txt", "/users.txt",
                "C:\\users.txt", "nested/users.txt", "users.csv")) {
            assertThrows(IllegalArgumentException.class, () -> storage.readLines(invalidName));
            assertThrows(IllegalArgumentException.class,
                    () -> storage.writeLines(invalidName, List.of("data")));
        }
    }
}
