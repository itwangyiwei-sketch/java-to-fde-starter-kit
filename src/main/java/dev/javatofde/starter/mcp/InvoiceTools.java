package dev.javatofde.starter.mcp;

import dev.javatofde.starter.domain.Invoice;
import dev.javatofde.starter.domain.PurchaseOrder;
import dev.javatofde.starter.domain.ReconciliationDataSource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** Read-only MCP tools over synthetic records. Tenant IDs here are demo inputs, not authentication. */
@Service
public class InvoiceTools {
    private final ReconciliationDataSource repository;

    public InvoiceTools(ReconciliationDataSource repository) {
        this.repository = repository;
    }

    @Tool(name = "get_invoice", description = "Look up a synthetic invoice by tenant and invoice ID. Read only.")
    public Invoice getInvoice(
            @ToolParam(description = "Synthetic tenant ID, e.g. acme") String tenantId,
            @ToolParam(description = "Invoice ID, e.g. INV-1001") String invoiceId) {
        return repository.findInvoice(tenantId, invoiceId).orElse(null);
    }

    @Tool(name = "get_purchase_order", description = "Look up a synthetic purchase order by tenant and order ID. Read only.")
    public PurchaseOrder getPurchaseOrder(
            @ToolParam(description = "Synthetic tenant ID, e.g. acme") String tenantId,
            @ToolParam(description = "Purchase order ID, e.g. PO-1001") String purchaseOrderId) {
        return repository.findPurchaseOrder(tenantId, purchaseOrderId).orElse(null);
    }
}
