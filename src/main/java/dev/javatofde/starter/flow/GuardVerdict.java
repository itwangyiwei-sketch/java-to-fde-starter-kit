package dev.javatofde.starter.flow;

public record GuardVerdict(Decision decision, ReasonCode reasonCode, String reason) {
    static GuardVerdict ready() {
        return new GuardVerdict(Decision.READY_FOR_REVIEW, ReasonCode.MATCHED,
                "Invoice and purchase order match; a human must still approve the invoice.");
    }

    static GuardVerdict review(ReasonCode code, String reason) {
        return new GuardVerdict(Decision.MANUAL_REVIEW, code, reason);
    }
}
