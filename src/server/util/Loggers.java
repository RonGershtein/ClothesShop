// ============================================================================
// Short: central place to get Logger objects for different areas.
// Writes to logs/<name>.log (creates dir/file); if file logging fails → console.
// ============================================================================

package server.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.*;

public class Loggers {
    public static Logger system() { return logger("system.log"); }
    public static Logger auth()   { return logger("auth.log"); }
    public static Logger employees() { return logger("employees.log"); }
    public static Logger customers() { return logger("customers.log"); }
    public static Logger transactions() { return logger("transactions.log"); }
    public static Logger chat() { return logger("chat.log"); }

    // ------------------------------------------------------------------------
    // logger(name): returns a cached java.util.Logger.
    // If no handlers yet: create logs/ directory, attach FileHandler (append).
    // On failure: attach ConsoleHandler instead. Disable parent handlers.
    // ------------------------------------------------------------------------

    private static Logger logger(String name) {
        Logger l = Logger.getLogger(name);
        if (l.getHandlers().length == 0) {
            try {
                // Use absolute path to avoid path issues
                Path logsDir = Path.of(System.getProperty("user.dir"), "logs");
                Files.createDirectories(logsDir);
                
                // Try to create the log file with append mode
                FileHandler fh = new FileHandler(logsDir.resolve(name).toString(), true);
                fh.setFormatter(new SimpleFormatter());
                l.addHandler(fh);
                l.setUseParentHandlers(false);
            } catch (IOException e) {
                // If logging fails, just log to console instead of crashing
                System.err.println("Warning: Could not create log file " + name + ": " + e.getMessage());
                ConsoleHandler ch = new ConsoleHandler();
                ch.setFormatter(new SimpleFormatter());
                l.addHandler(ch);
                l.setUseParentHandlers(false);
            }
        }
        return l;
    }
}
