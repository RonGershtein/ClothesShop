// ============================================================================
// Inventory service for the store.
// Reads/writes products from data/products.txt and provides:
// - listing by branch, finding by SKU/category
// - updating stock quantities (with simple logging)
// - choosing the cheapest available item in a category
// ============================================================================

package server.domain.invantory;

import server.util.FileDatabase;
import server.util.Loggers;
import server.shared.Branch;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class InventoryService {

    // Underlying flat-file DB holding products (CSV lines)
    private final FileDatabase productsDb = new FileDatabase(Path.of("data/products.txt"));

    // ------------------------------------------------------------------------
    // listByBranch(): returns all products that belong to a given branch.
    // Skips blank/comment lines, parses each line to Product, then filters by branch.
    // ------------------------------------------------------------------------
    public synchronized List<Product> listByBranch(Branch branch) {
        return productsDb.readAllLines().stream()
                .filter(s -> !s.isBlank() && !s.startsWith("#"))
                .map(this::parseProduct)
                .filter(p -> p.branch() == branch)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------------
    // findProduct(): looks for a single product by SKU within a specific branch.
    // Uses listByBranch() and returns the first exact SKU match (if any).
    // ------------------------------------------------------------------------
    public synchronized Optional<Product> findProduct(Branch branch, String sku) {
        return listByBranch(branch).stream().filter(p -> p.sku().equals(sku)).findFirst();
    }

    // ------------------------------------------------------------------------
    // updateQuantity(): changes stock for a given SKU in a branch by delta.
    // Positive delta = add stock; negative delta = reduce stock (never below 0).
    // Rewrites the file line for that product and logs the action.
    // ------------------------------------------------------------------------
    public synchronized void updateQuantity(Branch branch, String sku, int delta) {
        List<String> lines = productsDb.readAllLines();
        boolean updated = false;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            if (s.isBlank() || s.startsWith("#")) continue;
            Product p = parseProduct(s);
            if (p.branch() == branch && p.sku().equals(sku)) {
                int newQuantity = Math.max(0, p.quantity() + delta); // keep non-negative
                Product np = new Product(p.sku(), p.category(), p.branch(), newQuantity, p.price());
                lines.set(i, formatProduct(np));                     // replace CSV line
                productsDb.writeAllLines(lines);                     // persist changes
                updated = true;

                // Log the transaction (ordered in, or sold out)
                if (delta > 0) {
                    Loggers.transactions().info(String.format(
                            "STOCK_ORDERED: Branch=%s, ID=%s, Category=%s, Quantity=%d, Price=%s",
                            branch.name(), sku, p.category(), delta, p.price()));
                } else if (delta < 0) {
                    Loggers.transactions().info(String.format(
                            "STOCK_SOLD: Branch=%s, ID=%s, Category=%s, Quantity=%d, Price=%s",
                            branch.name(), sku, p.category(), Math.abs(delta), p.price()));
                }
                break;
            }
        }
        if (!updated) throw new IllegalStateException("SKU not found for update: " + sku + " at " + branch);
    }

    // ------------------------------------------------------------------------
    // parseProduct(): converts one CSV line into a Product object.
    // Expected order: sku,category,branch,quantity,price.
    // ------------------------------------------------------------------------
    private Product parseProduct(String s) {
        String[] t = s.split(",", -1); // sku,category,branch,quantity,price
        Product p = new Product(
                t[0], t[1], Branch.valueOf(t[2]),
                Integer.parseInt(t[3]),
                new BigDecimal(t[4])
        );
        return p;
    }

    // ------------------------------------------------------------------------
    // formatProduct(): converts a Product back into one CSV line.
    // Keeps numeric fields as plain strings for stable persistence.
    // ------------------------------------------------------------------------
    private String formatProduct(Product p) {
        return String.join(",",
                p.sku(), p.category(), p.branch().name(),
                String.valueOf(p.quantity()), p.price().toPlainString()
        );
    }

    // ------------------------------------------------------------------------
    // findAnyByCategory(): finds any available product in a category (qty > 0).
    // Preference: the cheapest item first (sorted by unit price).
    // ------------------------------------------------------------------------
    public synchronized Optional<Product> findAnyByCategory(Branch branch, String category) {
        return listByBranch(branch).stream()
                .filter(p -> p.category().equalsIgnoreCase(category) && p.quantity() > 0)
                .sorted(Comparator.comparing(Product::price)) // cheapest first
                .findFirst();
    }

    // ------------------------------------------------------------------------
    // consumeOneByCategory(): reduces stock by 1 for a chosen product in category.
    // Picks the cheapest available item via findAnyByCategory(); returns true if reduced.
    // ------------------------------------------------------------------------
    /** מוריד יחידה אחת ממוצר כלשהו בקטגוריה הנתונה ומעדכן מלאי. מחזיר true אם הצליח */
    public synchronized boolean consumeOneByCategory(Branch branch, String category) {
        Optional<Product> opt = findAnyByCategory(branch, category);
        if (opt.isEmpty()) return false;
        Product p = opt.get();
        updateQuantity(branch, p.sku(), -1); // consume one unit
        return true;
    }
}
