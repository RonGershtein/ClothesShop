// ============================================================================
// Manages employees in memory and on disk (CSV file under data/employees.txt).
// Loads existing employees at startup, supports add/delete/find/list, and
// assigns sequential IDs like E00001. Thread-safe for writes.
// ============================================================================

package server.domain.employees;

import server.shared.Branch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * EmployeeDirectory (ללא תאימות לאחור, פשוט ונקי).
 * שומר עובדים ב- data/employees.txt בפורמט CSV של 9 עמודות:
 * employeeId,username,passwordHash,role,branch,accountNumber,phone,fullName,nationalId
 *
 * אם הקובץ לא קיים - ייווצר ריק.
 */
public final class EmployeeDirectory {

    private static final Path DATA_DIR = Paths.get("data");
    private static final Path EMP_FILE = DATA_DIR.resolve("employees.txt");

    private final Map<String, Employee> byId = new ConcurrentHashMap<>();
    private final Map<String, Employee> byUsername = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------------
    // ctor: ensures the data folder/file exist and loads all employees to memory.
    // If file is empty/missing, starts with an empty directory.
    // ------------------------------------------------------------------------
    public EmployeeDirectory() {
        try {
            if (!Files.exists(DATA_DIR)) Files.createDirectories(DATA_DIR);
            if (!Files.exists(EMP_FILE)) Files.createFile(EMP_FILE);
            load(); // read CSV -> maps
        } catch (IOException e) {
            throw new IllegalStateException("init EmployeeDirectory failed", e);
        }
    }

    // ------------------------------------------------------------------------
    // addEmployee(): adds a new employee record (passwordHash is already hashed).
    // Validates inputs and uniqueness, generates a new E-number, persists to file.
    // ------------------------------------------------------------------------
    // Note: expects passwordHash already computed (e.g., sha256)
    public synchronized Employee addEmployee(String username, String passwordHash, String role, Branch branch,
                                             String accountNumber, String phone, String fullName, String nationalId) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(branch, "branch");

        if (byUsername.containsKey(username)) {
            throw new IllegalArgumentException("USERNAME_ALREADY_EXISTS");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("FULL_NAME_REQUIRED");
        }
        if (nationalId == null || nationalId.isBlank()) {
            throw new IllegalArgumentException("NATIONAL_ID_REQUIRED");
        }
        if (containsComma(username, accountNumber, phone, fullName, nationalId)) {
            throw new IllegalArgumentException("NO_COMMAS_ALLOWED");
        }

        String id = nextEmployeeId(); // e.g., E00001, E00002, ...
        Employee emp = new Employee(id, username, passwordHash, role, branch, accountNumber, phone, fullName, nationalId);
        byId.put(id, emp);
        byUsername.put(username, emp);
        save(); // write entire table back to file
        return emp;
    }

    // ------------------------------------------------------------------------
    // deleteById(): removes an employee by id and saves the file.
    // Returns true if removed, false if id did not exist.
    // ------------------------------------------------------------------------
    public synchronized boolean deleteById(String employeeId) {
        Employee e = byId.remove(employeeId);
        if (e != null) {
            byUsername.remove(e.username());
            saveSilently(); // ignore IO errors here
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // findById(): quick lookup from the in-memory map.
    // ------------------------------------------------------------------------
    public Optional<Employee> findById(String employeeId) {
        return Optional.ofNullable(byId.get(employeeId));
    }

    // ------------------------------------------------------------------------
    // findByUsername(): quick lookup from the in-memory map.
    // ------------------------------------------------------------------------
    public Optional<Employee> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }

    // ------------------------------------------------------------------------
    // listAll(): returns all employees sorted by their employeeId.
    // ------------------------------------------------------------------------
    public List<Employee> listAll() {
        return byId.values().stream()
                .sorted(Comparator.comparing(Employee::employeeId))
                .toList();
    }


    // ==================== I/O ====================

    // ------------------------------------------------------------------------
    // load(): reads the CSV file into the maps (skips empty/comment lines).
    // ------------------------------------------------------------------------
    private void load() throws IOException {
        byId.clear(); byUsername.clear();
        try (BufferedReader br = Files.newBufferedReader(EMP_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Employee e = Employee.fromCsv(line); // parse a single CSV row
                byId.put(e.employeeId(), e);
                byUsername.put(e.username(), e);
            }
        }
    }

    // ------------------------------------------------------------------------
    // save(): writes the current in-memory list back to CSV (overwrites file).
    // Keeps rows sorted by employeeId for stable ordering.
    // ------------------------------------------------------------------------
    private void save() {
        try (BufferedWriter bw = Files.newBufferedWriter(EMP_FILE, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            for (Employee e : listAll()) { // listAll() is already sorted
                bw.write(e.toCsv());
                bw.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("save employees failed", e);
        }
    }

    // ------------------------------------------------------------------------
    // saveSilently(): wrapper around save() that swallows exceptions.
    // Useful on delete operations where failing to save shouldn't crash the app.
    // ------------------------------------------------------------------------
    private void saveSilently() { try { save(); } catch (Exception ignored) {} }

    // ==================== Helpers ====================

    // ------------------------------------------------------------------------
    // nextEmployeeId(): returns the next sequential id in the format E#####.
    // Scans current IDs, finds max numeric part, and increments by 1.
    // ------------------------------------------------------------------------
    private String nextEmployeeId() {
        int max = 0;
        for (String id : byId.keySet()) {
            if (id != null && id.startsWith("E")) {
                try { max = Math.max(max, Integer.parseInt(id.substring(1))); } catch (NumberFormatException ignored) {}
            }
        }
        return String.format("E%05d", max + 1);
    }

    // ------------------------------------------------------------------------
    // containsComma(): returns true if any provided string contains a comma.
    // Used to protect the CSV format (no commas inside fields).
    // ------------------------------------------------------------------------
    private static boolean containsComma(String... s) {
        for (String x : s) { if (x != null && x.contains(",")) return true; }
        return false;
    }
}
