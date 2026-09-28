package dev.javatofde.starter.flow;

import dev.javatofde.starter.domain.Invoice;
import dev.javatofde.starter.domain.PurchaseOrder;
import org.springframework.stereotype.Component;

/** Deterministic business invariants; model output cannot bypass them. */
@Component
public class BusinessGuard {
    public GuardVerdict check(Invoice invoice, PurchaseOrder order) {
        if (invoice.duplicate()) {
            return GuardVerdict.review(ReasonCode.DUPLICATE_INVOICE, "Invoice is marked as a duplicate.");
        }
        if (!invoice.documentComplete()) {
            return GuardVerdict.review(ReasonCode.DOCUMENT_INCOMPLETE, "Required document is missing.");
        }
        if (order == null) {
            return GuardVerdict.review(ReasonCode.PURCHASE_ORDER_MISSING, "Purchase order was not found.");
        }
        if (!order.active()) {
            return GuardVerdict.review(ReasonCode.PURCHASE_ORDER_INACTIVE, "Purchase order is inactive.");
        }
        if (!invoice.vendor().equals(order.vendor())) {
            return GuardVerdict.review(ReasonCode.VENDOR_MISMATCH, "Vendor does not match the purchase order.");
        }
        if (!invoice.currency().equals(order.currency())) {
            return GuardVerdict.review(ReasonCode.CURRENCY_MISMATCH, "Currency does not match the purchase order.");
        }
        if (invoice.amount().compareTo(order.amount()) != 0) {
            return GuardVerdict.review(ReasonCode.AMOUNT_MISMATCH, "Amount does not match the purchase order.");
        }
        return GuardVerdict.ready();
    }
}
