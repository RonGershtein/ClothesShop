// ============================================================================
// Customer service for reading/writing customers and their purchase stats.
// Stores data in simple text files, updates customer tier after purchases.
// ============================================================================

package server.domain.customers;

import server.util.FileDatabase;
import server.util.Loggers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Customers are stored in: data/customers.txt
 *   line: id,fullName,phone,type   (type: NEW | RETURNING | VIP)
 *
 * Purchase counts are stored in: data/customer_stats.txt
 *   line: id,count
 *
 * Auto-promotion thresholds:
 *   >= 10 purchases => VIP
 *   >=  2 purchases => RETURNING
 *   else            => NEW
 */
public class CustomerService {

    private final FileDatabase customersDb = new FileDatabase(Path.of("data/customers.txt"));
    private final FileDatabase statsDb     = new FileDatabase(Path.of("data/customer_stats.txt"));

    // ------------------------------------------------------------------------
    // findById(): look up a customer by ID in customers.txt.
    // Skips blank/comment lines; returns Optional.empty() if not found.
    // ------------------------------------------------------------------------
    /** Find by ID in customers file. */
    public Optional<Customer> findById(String id) {
        for (String s : customersDb.readAllLines()) {
            if (s == null) continue;
            String line = s.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] t = line.split(",", -1); // id,fullName,phone,type
            if (t.length < 4) continue;
            if (t[0].equals(id)) {
                return Optional.of(new Customer(t[0], t[1], t[2], typeFrom(t[3])));
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------------
    // listAll(): read all customers from file into a list.
    // Ignores blank/comment lines; maps CSV lines into Customer records.
    // ------------------------------------------------------------------------
    /** List all customers. */
    public List<Customer> listAll() {
        List<Customer> out = new ArrayList<>();
        for (String s : customersDb.readAllLines()) {
            if (s == null) continue;
            String line = s.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split(",", -1);
            if (t.length < 4) continue;
            out.add(new Customer(t[0], t[1], t[2], typeFrom(t[3])));
        }
        return out;
    }

    // ------------------------------------------------------------------------
    // upsert(): insert or update a customer by ID.
    // Rewrites the file with the new/updated CSV row.
    // ------------------------------------------------------------------------
    /** Insert or update by id. */
    public void upsert(Customer customer) {
        List<String> lines = new ArrayList<>(customersDb.readAllLines());
        boolean found = false;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] t = line.split(",", -1);
            if (t.length < 4) continue;

            if (t[0].equals(customer.id())) {
                lines.set(i, format(customer));
                found = true;
                break;
            }
        }
        if (!found) lines.add(format(customer));
        customersDb.writeAllLines(lines);
    }

    // ------------------------------------------------------------------------
    // addCustomer(): create a new customer row (fails if ID exists).
    // Also ensures a stats row is created with count=0 and logs the action.
    // ------------------------------------------------------------------------
    /** Add a new customer (fails if id already exists). */
    public Customer addCustomer(String id, String fullName, String phone, String typeCode) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Customer ID is required");
        if (findById(id).isPresent()) throw new IllegalArgumentException("Customer already exists: " + id);

        CustomerType type = typeFrom(typeCode);
        Customer c = new Customer(id, fullName == null ? "" : fullName, phone == null ? "" : phone, type);
        upsert(c);
        // initialize stats at zero
        ensureStatsRow(id);

        // Log the customer addition
        Loggers.customers().info(String.format("CUSTOMER_ADDED: ID=%s, FullName=%s, Phone=%s, Type=%s",
                id, fullName, phone, typeCode));

        return c;
    }

    // ------------------------------------------------------------------------
    // recordPurchase(): increments the customer's purchase count.
    // If thresholds are crossed, updates the customer's type (NEW/RETURNING/VIP).
    // ------------------------------------------------------------------------
    /** Record a purchase and auto-promote type if thresholds reached. */
    public void recordPurchase(String id) {
        int count = incrementAndGetCount(id);
        String newTypeCode = tierForCount(count);

        // Update type in customers file if changed
        Customer current = findById(id)
                .orElseThrow(() -> new IllegalStateException("Customer not found: " + id));
        String currentCode = current.type().returnType();

        if (!currentCode.equals(newTypeCode)) {
            upsert(new Customer(current.id(), current.fullName(), current.phone(), typeFrom(newTypeCode)));
        }
    }

    // ==================== Helpers ====================

    // ------------------------------------------------------------------------
    // typeFrom(): converts a string code into a CustomerType instance.
    // Defaults to NEW if code is null/unknown.
    // ------------------------------------------------------------------------
    private CustomerType typeFrom(String code) {
        String c = code == null ? "NEW" : code.trim().toUpperCase();
        return switch (c) {
            case "VIP" -> new VipCustomer();
            case "RETURNING" -> new ReturningCustomer();
            default -> new NewCustomer();
        };
    }

    // ------------------------------------------------------------------------
    // tierForCount(): maps purchase count to tier code.
    // 10+ => VIP, 2+ => RETURNING, else NEW.
    // ------------------------------------------------------------------------
    private String tierForCount(int count) {
        if (count >= 10) return "VIP";
        if (count >= 2)  return "RETURNING";
        return "NEW";
    }

    // ------------------------------------------------------------------------
    // ensureStatsRow(): make sure every customer has a stats row starting at 0.
    // If ID is missing from stats file, append "id,0".
    // ------------------------------------------------------------------------
    private void ensureStatsRow(String id) {
        List<String> lines = new ArrayList<>(statsDb.readAllLines());
        for (String s : lines) {
            if (s == null) continue;
            String line = s.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split(",", -1);
            if (t.length < 2) continue;
            if (t[0].equals(id)) return; // already exists
        }
        lines.add(id + ",0");
        statsDb.writeAllLines(lines);
    }

    // ------------------------------------------------------------------------
    // incrementAndGetCount(): increase the purchase count for this ID by 1.
    // Updates/creates the stats row and returns the new count.
    // ------------------------------------------------------------------------
    private int incrementAndGetCount(String id) {
        List<String> lines = new ArrayList<>(statsDb.readAllLines());
        boolean updated = false;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            if (s == null) continue;
            String line = s.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split(",", -1);
            if (t.length < 2) continue;

            if (t[0].equals(id)) {
                int cur = parseIntSafe(t[1]);
                int next = cur + 1;
                lines.set(i, id + "," + next);
                statsDb.writeAllLines(lines);
                updated = true;
                return next;
            }
        }
        // no row -> create with 1
        lines.add(id + ",1");
        statsDb.writeAllLines(lines);
        return 1;
    }

    // ------------------------------------------------------------------------
    // parseIntSafe(): parse int with default 0 on any error.
    // Trims the string; catches exceptions to keep file parsing robust.
    // ------------------------------------------------------------------------
    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ------------------------------------------------------------------------
    // format(): convert a Customer into a CSV line for customers.txt.
    // Order: id,fullName,phone,typeCode
    // ------------------------------------------------------------------------
    private String format(Customer c) {
        return String.join(",",
                c.id(),
                c.fullName(),
                c.phone(),
                c.type().returnType()
        );
    }
}
