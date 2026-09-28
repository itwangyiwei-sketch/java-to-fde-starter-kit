package dev.javatofde.starter.domain;

import java.util.Optional;

/** Boundary for a future authorized ERP or database adapter. */
public interface ReconciliationDataSource {
    Optional<Invoice> findInvoice(String tenantId, String invoiceId);

    Optional<PurchaseOrder> findPurchaseOrder(String tenantId, String purchaseOrderId);
}
