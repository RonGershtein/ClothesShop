// ============================================================================
// Simple utility for reading/writing a single text file (UTF-8).
// Thread-safe (methods are synchronized). On read: returns empty list if file
// does not exist. On write: creates parent folders and overwrites the file.
// IOExceptions are wrapped as UncheckedIOException (no checked throws).
// ============================================================================

package server.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class FileDatabase {
    // Path of the file this instance manages
    private final Path path;

    // ------------------------------------------------------------------------
    // Constructor: store the target file path for later read/write operations.
    // ------------------------------------------------------------------------
    public FileDatabase(Path path) { this.path = path; }

    // ------------------------------------------------------------------------
    // readAllLines(): read all lines from the file in UTF-8.
    // If the file does not exist, return an empty list (no error).
    // Synchronized for simple thread-safety.
    // ------------------------------------------------------------------------
    public synchronized List<String> readAllLines() {
        try {
            if (Files.notExists(path)) return new ArrayList<>(); // no file yet -> empty list
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Convert checked IOException to unchecked so callers don't need try/catch
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------------
    // writeAllLines(lines): write the given lines to the file in UTF-8.
    // Ensures the parent directory exists. Overwrites the file content.
    // Synchronized for simple thread-safety.
    // ------------------------------------------------------------------------
    public synchronized void writeAllLines(List<String> lines) {
        try {
            Files.createDirectories(path.getParent()); // make sure folder exists
            Files.write(
                    path,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,            // create file if missing
                    StandardOpenOption.TRUNCATE_EXISTING  // replace previous content
            );
        } catch (IOException e) {
            // Convert checked IOException to unchecked so callers don't need try/catch
            throw new UncheckedIOException(e);
        }
    }

}
