package dev.javatofde.starter.api;

import dev.javatofde.starter.flow.Decision;
import dev.javatofde.starter.flow.FlowState;
import dev.javatofde.starter.flow.ReasonCode;
import java.util.List;

public record ReconcileResponse(
        String traceId,
        String invoiceId,
        Decision decision,
        ReasonCode reasonCode,
        String reason,
        List<FlowState> states,
        long durationMs,
        boolean guardOverrodeModel
) {
}
