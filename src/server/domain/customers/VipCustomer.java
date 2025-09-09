
package server.domain.customers;

import java.math.BigDecimal;

public final class VipCustomer implements CustomerType {

    @Override
    public String returnType() { return "VIP"; }

    @Override
    public BigDecimal applyDiscount(BigDecimal basePrice) {
        if (basePrice == null) return BigDecimal.ZERO;           // defensive: no price -> no discount
        return basePrice.multiply(new BigDecimal("0.12"));       // 12% discount amount
    }

    // ------------------------------------------------------------------------
    // qualifiesGiftShirt(finalTotal): checks if VIP gets a free shirt.
    // Condition: final total (after discount) >= 300.
    // ------------------------------------------------------------------------
    @Override
    public boolean qualifiesGiftShirt(BigDecimal finalTotal) {
        if (finalTotal == null) return false;                    // defensive: no total -> no gift
        return finalTotal.compareTo(new BigDecimal("300")) >= 0; // true when total >= 300
    }
}
