// ============================================================================
// Authentication service for the store application.
// - Verifies admin/employee credentials (employees are read from data/employees.txt)
// - Uses SHA-256 hashing for passwords
// - Tracks active users to block duplicate logins
// - Supports logout to free the username
// CSV format (employees.txt): employeeId,username,hash,role,branch,accountNumber,phone
// ============================================================================

package server.domain.employees;

import server.util.FileDatabase;
import server.util.Loggers;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class AuthService {
    private final FileDatabase employeesDb = new FileDatabase(Path.of("data/employees.txt")); // file-backed employee store
    private final Set<String> activeUsers = Collections.synchronizedSet(new HashSet<>());      // connected usernames

    // ------------------------------------------------------------------------
    // sha256(): returns the hex SHA-256 hash of the given string.
    // Used to compare plaintext password to the stored hash in the file.
    // ------------------------------------------------------------------------
    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes());              // hash bytes
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x)); // to lowercase hex
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);                   // should not happen on standard JREs
        }
    }

    // ------------------------------------------------------------------------
    // LoginResult: result of a login attempt (ok, bad creds, or already connected).
    // Helps the caller print the correct message to the user.
    // ------------------------------------------------------------------------
    public enum LoginResult {
        SUCCESS,
        INVALID_CREDENTIALS,
        ALREADY_CONNECTED
    }

    // ------------------------------------------------------------------------
    // loginAdmin(): special simple admin login ("admin"/"admin").
    // Also prevents the same admin username from logging in twice.
    // ------------------------------------------------------------------------
    public LoginResult loginAdmin(String username, String password) {
        // Easy admin (as requested)
        if ("admin".equals(username) && "admin".equals(password)) {
            synchronized (activeUsers) {
                if (activeUsers.contains(username)) {
                    return LoginResult.ALREADY_CONNECTED;    // block duplicate session
                }
                activeUsers.add(username);                   // mark as connected
            }
            return LoginResult.SUCCESS;
        }
        return LoginResult.INVALID_CREDENTIALS;              // any other creds are rejected
    }

    // ------------------------------------------------------------------------
    // loginEmployee(): authenticate an employee using employees.txt.
    // Compares username + SHA-256(password) against the CSV; blocks double login.
    // ------------------------------------------------------------------------
    public LoginResult loginEmployee(String username, String password) {
        String hash = sha256(password);
        for (String line : employeesDb.readAllLines()) {
            if (line.isBlank() || line.startsWith("#")) continue; // skip comments/empty
            String[] t = line.split(",", -1); // employeeId,username,hash,role,branch,accountNumber,phone
            if (t.length < 7) continue;       // ignore malformed rows
            if (t[1].equals(username) && t[2].equals(hash)) {
                synchronized (activeUsers) {
                    if (activeUsers.contains(username)) {
                        Loggers.auth().warning("Double login blocked: " + username);
                        return LoginResult.ALREADY_CONNECTED;
                    }
                    activeUsers.add(username); // mark as connected
                }
                Loggers.auth().info("Employee login OK: " + username);
                return LoginResult.SUCCESS;
            }
        }
        Loggers.auth().warning("Employee login FAIL: " + username);
        return LoginResult.INVALID_CREDENTIALS;
    }

    // ------------------------------------------------------------------------
    // logout(): removes the username from the active set.
    // After this, the same user can log in again from another session.
    // ------------------------------------------------------------------------
    public void logout(String username) {
        if (username != null) activeUsers.remove(username);
    }
}
