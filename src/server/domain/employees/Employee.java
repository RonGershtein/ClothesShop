// ============================================================================
// Employee domain model used across the project.
// Holds login/HR details and supports CSV serialize/parse for file storage.
// ============================================================================

package server.domain.employees;

import server.shared.Branch;

public final class Employee {
    private final String employeeId;
    private final String username;
    private final String passwordHash; // stored as SHA-256 hash
    private final String role;         // SALESPERSON/CASHIER/SHIFT_MANAGER
    private final Branch branch;       // HOLON/TEL_AVIV/RISHON
    private final String accountNumber;
    private final String phone;
    private final String fullName;     // extra info
    private final String nationalId;   // extra info

    // ------------------------------------------------------------------------
    // Constructor: builds an immutable employee object with all fields.
    // Used when reading from CSV or creating a new record in memory.
    // ------------------------------------------------------------------------
    public Employee(String employeeId, String username, String passwordHash,
                    String role, Branch branch, String accountNumber, String phone,
                    String fullName, String nationalId) {
        this.employeeId = employeeId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.branch = branch;
        this.accountNumber = accountNumber;
        this.phone = phone;
        this.fullName = fullName;
        this.nationalId = nationalId;
    }

    // ------------------------------------------------------------------------
    // Returns the unique employee id (string as stored in CSV).
    // ------------------------------------------------------------------------
    public String employeeId()   { return employeeId; }

    // ------------------------------------------------------------------------
    // Returns the login username.
    // ------------------------------------------------------------------------
    public String username()     { return username; }

    // ------------------------------------------------------------------------
    // Returns the employee's role code (e.g., SALESPERSON).
    // ------------------------------------------------------------------------
    public String role()         { return role; }

    // ------------------------------------------------------------------------
    // Returns the branch this employee belongs to.
    // ------------------------------------------------------------------------
    public Branch branch()       { return branch; }

    // ------------------------------------------------------------------------
    // Returns the bank account number (as provided).
    // ------------------------------------------------------------------------
    public String accountNumber(){ return accountNumber; }

    // ------------------------------------------------------------------------
    // Returns the phone number (no formatting enforced).
    // ------------------------------------------------------------------------
    public String phone()        { return phone; }

    // ------------------------------------------------------------------------
    // Returns the full name for display/forms.
    // ------------------------------------------------------------------------
    public String fullName()     { return fullName; }

    // ------------------------------------------------------------------------
    // Returns the national ID for HR records.
    // ------------------------------------------------------------------------
    public String nationalId()   { return nationalId; }

    // ------------------------------------------------------------------------
    // toCsv(): converts this object into one CSV line (9 fields, no commas inside).
    // Field order matches the documented format for persistence.
    // ------------------------------------------------------------------------
    public String toCsv() {
        return String.join(",",
                employeeId, username, passwordHash, role, branch.name(),
                accountNumber, phone, fullName, nationalId
        );
    }

    // ------------------------------------------------------------------------
    // fromCsv(): parses a CSV line into an Employee instance.
    // Validates there are exactly 9 fields; throws if malformed.
    // ------------------------------------------------------------------------
    public static Employee fromCsv(String line) {
        String[] t = line.split(",", -1);
        if (t.length != 9) throw new IllegalArgumentException("BAD_EMP_CSV");
        return new Employee(
                t[0], t[1], t[2], t[3],
                server.shared.Branch.valueOf(t[4]),
                t[5], t[6], t[7], t[8]
        );
    }
}
