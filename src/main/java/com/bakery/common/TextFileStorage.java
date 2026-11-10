package com.bakery.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    /**
     * Replaces a complete file without leaving it half-written: the lines go to a temporary file
     * in the same directory, which is then moved over the target. Added for User Management;
     * {@link #writeLines} is unchanged for other modules.
     */
    public void writeLinesAtomically(String fileName, List<String> lines) throws IOException {
        Path file = resolve(fileName);
        Files.createDirectories(dataDirectory);
        Path temporary = Files.createTempFile(dataDirectory, fileName + ".", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            moveIntoPlace(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Atomic replace where supported; retries briefly because Windows/OneDrive can hold short locks. */
    private static void moveIntoPlace(Path source, Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (AccessDeniedException e) {
                if (attempt >= 5) {
                    throw e;
                }
                try {
                    Thread.sleep(40L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private Path resolve(String fileName) {
        // Accept a simple .txt filename, never an absolute path or subdirectory.
        if (fileName == null || !fileName.matches("[a-zA-Z0-9][a-zA-Z0-9_-]*\\.txt")) {
            throw new IllegalArgumentException("Use a simple .txt filename, for example users.txt");
        }
        return dataDirectory.resolve(fileName);
    }
}
