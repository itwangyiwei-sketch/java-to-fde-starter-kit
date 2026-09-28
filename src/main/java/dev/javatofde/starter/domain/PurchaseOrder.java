package dev.javatofde.starter.domain;

import java.math.BigDecimal;

public record PurchaseOrder(
        String id,
        String tenantId,
        BigDecimal amount,
        String currency,
        String vendor,
        boolean active
) {
}
