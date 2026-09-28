package dev.javatofde.starter.model;

import dev.javatofde.starter.domain.Invoice;
import dev.javatofde.starter.domain.PurchaseOrder;
import dev.javatofde.starter.flow.Decision;
import dev.javatofde.starter.flow.ReasonCode;

/** Deterministic model substitute for the first run and regression evals. */
public class MockProposalModel implements ProposalModel {
    @Override
    public Proposal propose(String query, Invoice invoice, PurchaseOrder order, DemoFault fault) {
        if (fault == DemoFault.TIMEOUT) {
            throw new ModelFailure(ReasonCode.MODEL_TIMEOUT);
        }
        if (fault == DemoFault.MALFORMED) {
            throw new ModelFailure(ReasonCode.MODEL_INVALID_RESPONSE);
        }
        if (fault == DemoFault.OVERAPPROVE) {
            return new Proposal(invoice.id(), Decision.READY_FOR_REVIEW);
        }
        boolean likelyMatch = order != null
                && !invoice.duplicate()
                && invoice.documentComplete()
                && invoice.amount().compareTo(order.amount()) == 0;
        return new Proposal(invoice.id(), likelyMatch ? Decision.READY_FOR_REVIEW : Decision.MANUAL_REVIEW);
    }
}
