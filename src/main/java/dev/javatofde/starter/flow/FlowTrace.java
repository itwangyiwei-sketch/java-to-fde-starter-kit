package dev.javatofde.starter.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit, bounded transitions: no free-running agent loop. */
final class FlowTrace {
    private static final Map<FlowState, Set<FlowState>> ALLOWED = Map.of(
            FlowState.RECEIVED, Set.of(FlowState.INPUT_VALIDATED, FlowState.FALLBACK),
            FlowState.INPUT_VALIDATED, Set.of(FlowState.DATA_LOADED, FlowState.FALLBACK),
            FlowState.DATA_LOADED, Set.of(FlowState.MODEL_PROPOSED, FlowState.FALLBACK),
            FlowState.MODEL_PROPOSED, Set.of(FlowState.GUARDED, FlowState.FALLBACK),
            FlowState.GUARDED, Set.of(FlowState.COMPLETED, FlowState.FALLBACK)
    );

    private final List<FlowState> states = new ArrayList<>(List.of(FlowState.RECEIVED));

    void moveTo(FlowState next) {
        FlowState current = states.get(states.size() - 1);
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("Invalid workflow transition: " + current + " -> " + next);
        }
        states.add(next);
    }

    List<FlowState> states() {
        return List.copyOf(states);
    }
}
