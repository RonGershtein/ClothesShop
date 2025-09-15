package server.domain.customers;
// Simple customer data model (immutable).

public record Customer(
        String id,
        String fullName,
        String phone,
        CustomerType type
) {}
