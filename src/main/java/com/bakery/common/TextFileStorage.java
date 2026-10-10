package com.bakery.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Small UTF-8 file helper for module repositories.
 * Reading never creates or changes files. Writing replaces a complete file.
 * Record formats, validation, and CRUD belong to the individual modules.
 */
@Component
public class TextFileStorage {

    private final Path dataDirectory;

    public TextFileStorage(@Value("${bakery.data-directory:data}") String dataDirectory) {
        this.dataDirectory = Path.of(dataDirectory).toAbsolutePath().normalize();
    }

    public List<String> readLines(String fileName) throws IOException {
        return Files.readAllLines(resolve(fileName), StandardCharsets.UTF_8);
    }

    public void writeLines(String fileName, List<String> lines) throws IOException {
        Path file = resolve(fileName);
        Files.createDirectories(dataDirectory);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private Path resolve(String fileName) {
        // Accept a simple .txt filename, never an absolute path or subdirectory.
        if (fileName == null || !fileName.matches("[a-zA-Z0-9][a-zA-Z0-9_-]*\\.txt")) {
            throw new IllegalArgumentException("Use a simple .txt filename, for example users.txt");
        }
        return dataDirectory.resolve(fileName);
    }
}
