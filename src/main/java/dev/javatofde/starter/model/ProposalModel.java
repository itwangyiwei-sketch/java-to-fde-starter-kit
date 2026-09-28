package dev.javatofde.starter.model;

import dev.javatofde.starter.domain.Invoice;
import dev.javatofde.starter.domain.PurchaseOrder;

/** Replaceable model adapter. Only its proposal crosses into the guarded workflow. */
public interface ProposalModel {
    Proposal propose(String query, Invoice invoice, PurchaseOrder order, DemoFault demoFault);
}
