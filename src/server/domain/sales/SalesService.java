// ============================================================================
// Calculates sale totals for a single product purchase.
// - Applies customer-type discount on (unit price * quantity)
// - Returns a small summary (base, discount, final, customer type code)
// ============================================================================

package server.domain.sales;

import server.domain.customers.Customer;
import server.domain.invantory.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SalesService {

    // ------------------------------------------------------------------------
    // Holds the calculated results of a sale:
    // - basePrice: unit price * quantity (before discount)
    // - discountValue: how much was subtracted (depends on customer type)
    // - finalPrice: basePrice - discountValue
    // - customerTypeCode: "NEW" / "RETURNING" / "VIP"
    // ------------------------------------------------------------------------
    public static class SaleSummary {
        private final BigDecimal basePrice;
        private final BigDecimal discountValue;
        private final BigDecimal finalPrice;
        private final String customerTypeCode;

        // --------------------------------------------------------------------
        // Constructs an immutable sale summary. Values are expected to be
        // already scaled/rounded by the caller.
        // --------------------------------------------------------------------
        public SaleSummary(BigDecimal basePrice, BigDecimal discountValue, BigDecimal finalPrice, String customerTypeCode) {
            this.basePrice = basePrice;
            this.discountValue = discountValue;
            this.finalPrice = finalPrice;
            this.customerTypeCode = customerTypeCode;
        }

        // --------------------------------------------------------------------
        // Getters (no logic, just expose the fields).
        // --------------------------------------------------------------------
        public BigDecimal basePrice()     { return basePrice; }
        public BigDecimal discountValue() { return discountValue; }
        public BigDecimal finalPrice()    { return finalPrice; }
        public String customerTypeCode()  { return customerTypeCode; }
    }

    // ------------------------------------------------------------------------
    // sell(): calculates a sale for a given product and quantity for a customer.
    // Steps:
    // 1) base = product.price * quantity
    // 2) discount = customer.type.applyDiscount(base)
    // 3) final = base - discount
    // Returns a SaleSummary with 2-decimal rounding.
    // ------------------------------------------------------------------------
    public SaleSummary sell(Product product, int quantity, Customer customer) {
        BigDecimal basePrice = product.price().multiply(BigDecimal.valueOf(quantity));
        BigDecimal discountValue = customer.type().applyDiscount(basePrice);
        BigDecimal finalPrice = basePrice.subtract(discountValue);
        return new SaleSummary(scale(basePrice), scale(discountValue), scale(finalPrice), customer.type().returnType());
    }

    // ------------------------------------------------------------------------
    // scale(): rounds a monetary value to 2 decimals using HALF_UP.
    // Keeps money formatting consistent for outputs and logs.
    // ------------------------------------------------------------------------
    private static BigDecimal scale(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
