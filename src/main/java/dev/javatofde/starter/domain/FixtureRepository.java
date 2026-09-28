package dev.javatofde.starter.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Synthetic, in-memory records. Replace this adapter before connecting enterprise data. */
@Repository
public class FixtureRepository implements ReconciliationDataSource {
    private final Map<String, Invoice> invoices = Map.of(
            "INV-1001", invoice("INV-1001", "acme", "PO-1001", "1000.00", false, true),
            "INV-1002", invoice("INV-1002", "acme", "PO-1002", "1200.00", false, true),
            "INV-1003", invoice("INV-1003", "acme", "PO-1003", "700.00", true, true),
            "INV-1004", invoice("INV-1004", "acme", "PO-404", "450.00", false, true),
            "INV-1005", invoice("INV-1005", "acme", "PO-1005", "900.00", false, false),
            "INV-2001", invoice("INV-2001", "beta", "PO-2001", "300.00", false, true)
    );

    private final Map<String, PurchaseOrder> purchaseOrders = Map.of(
            "PO-1001", order("PO-1001", "acme", "1000.00"),
            "PO-1002", order("PO-1002", "acme", "1000.00"),
            "PO-1003", order("PO-1003", "acme", "700.00"),
            "PO-1005", order("PO-1005", "acme", "900.00"),
            "PO-2001", order("PO-2001", "beta", "300.00")
    );

    @Override
    public Optional<Invoice> findInvoice(String tenantId, String invoiceId) {
        return Optional.ofNullable(invoices.get(invoiceId))
                .filter(invoice -> invoice.tenantId().equals(tenantId));
    }

    @Override
    public Optional<PurchaseOrder> findPurchaseOrder(String tenantId, String purchaseOrderId) {
        return Optional.ofNullable(purchaseOrders.get(purchaseOrderId))
                .filter(order -> order.tenantId().equals(tenantId));
    }

    private static Invoice invoice(String id, String tenant, String orderId, String amount,
                                   boolean duplicate, boolean documentComplete) {
        return new Invoice(id, tenant, orderId, new BigDecimal(amount), "CNY",
                "Example Supplies", duplicate, documentComplete);
    }

    private static PurchaseOrder order(String id, String tenant, String amount) {
        return new PurchaseOrder(id, tenant, new BigDecimal(amount), "CNY", "Example Supplies", true);
    }
}
