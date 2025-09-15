// ============================================================================
// Stores and loads the password policy used when creating employees.
// Persists settings in data/password_policy.txt so they survive restarts.
// ============================================================================

package server.domain.employees;

import server.util.FileDatabase;

import java.nio.file.Path;
import java.util.List;

/**
 * PasswordPolicy with persistence:
 * File: data/password_policy.txt
 * Format:
 *   minimumLength=6
 *   requireSpecialChar=true
 *   requireLetter=true
 */
public class PasswordPolicy {
    // Current policy in memory (defaults if file missing/corrupt)
    private static int minimumLength = 6;
    private static boolean requireSpecialChar = true;
    private static boolean requireLetter = true;

    private static final FileDatabase db = new FileDatabase(Path.of("data/password_policy.txt"));

    // ------------------------------------------------------------------------
    // On class load, try to read existing policy from file.
    // If it fails, defaults above remain.
    // ------------------------------------------------------------------------
    static { load(); }

    // ------------------------------------------------------------------------
    // configure(): set new policy values and save them to file.
    // Ensures minimumLength is at least 1. Thread-safe (synchronized).
    // ------------------------------------------------------------------------
    public static synchronized void configure(int minLen, boolean digit, boolean letter) {
        minimumLength = Math.max(1, minLen); // clamp to >= 1
        requireSpecialChar = digit;
        requireLetter = letter;
        save(); // write to data/password_policy.txt
    }

    // ------------------------------------------------------------------------
    // minimumLength(): current minimum length requirement.
    // ------------------------------------------------------------------------
    public static int minimumLength() { return minimumLength; }

    // ------------------------------------------------------------------------
    // requireSpecialChar(): whether at least one non-alphanumeric is required
    // (project maps this flag to "special char" in the UI).
    // ------------------------------------------------------------------------
    public static boolean requireSpecialChar() { return requireSpecialChar; }

    // ------------------------------------------------------------------------
    // requireLetter(): whether at least one letter is required.
    // ------------------------------------------------------------------------
    public static boolean requireLetter() { return requireLetter; }

    // ------------------------------------------------------------------------
    // load(): read policy lines from file and update in-memory values.
    // Skips blank/comment lines; ignores bad lines silently.
    // ------------------------------------------------------------------------
    private static void load() {
        try {
            List<String> lines = db.readAllLines();
            for (String s : lines) {
                if (s == null) continue;
                String line = s.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv = line.split("=", 2);
                if (kv.length != 2) continue;
                String k = kv[0].trim(), v = kv[1].trim();
                switch (k) {
                    case "minimumLength": minimumLength = Integer.parseInt(v); break;
                    case "requireSpecialChar":  requireSpecialChar = Boolean.parseBoolean(v); break;
                    case "requireLetter": requireLetter = Boolean.parseBoolean(v); break;
                }
            }
        } catch (Exception ignored) {
            // keep defaults if file missing or malformed
        }
    }

    // ------------------------------------------------------------------------
    // save(): write current policy to file (overwrites existing content).
    // Uses simple key=value format (3 lines).
    // ------------------------------------------------------------------------
    private static void save() {
        db.writeAllLines(List.of(
                "minimumLength=" + minimumLength,
                "requireSpecialChar=" + requireSpecialChar,
                "requireLetter=" + requireLetter
        ));
    }
}
