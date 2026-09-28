package dev.javatofde.starter.domain;

import java.math.BigDecimal;

public record Invoice(
        String id,
        String tenantId,
        String purchaseOrderId,
        BigDecimal amount,
        String currency,
        String vendor,
        boolean duplicate,
        boolean documentComplete
) {
}
