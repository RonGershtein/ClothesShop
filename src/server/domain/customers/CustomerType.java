// ============================================================================
// Customer type contract.
// Defines behavior for pricing and perks per customer category (e.g., NEW,
// RETURNING, VIP). Used by sales logic and CSV persistence.
// ============================================================================

package server.domain.customers;

import java.math.BigDecimal;

public interface CustomerType {

    // ------------------------------------------------------------------------
    // returnType(): returns the code/name of this type.
    // Example values: "NEW", "RETURNING", "VIP" (used in files/protocol).
    // ------------------------------------------------------------------------
    String returnType();

    // ------------------------------------------------------------------------
    // applyDiscount(basePrice): returns the monetary discount for this type.
    // Input: basePrice before discounts. Output: amount to subtract (>= 0).
    // ------------------------------------------------------------------------
    java.math.BigDecimal applyDiscount(BigDecimal basePrice);

    // ------------------------------------------------------------------------
    // qualifiesGiftShirt(finalTotal): indicates if a free shirt is granted.
    // Input: finalTotal after discounts. Default is false; types can override.
    // ------------------------------------------------------------------------
    default boolean qualifiesGiftShirt(BigDecimal finalTotal) {
        return false;
    }
}
