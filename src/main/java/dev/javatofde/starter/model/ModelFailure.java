package dev.javatofde.starter.model;

import dev.javatofde.starter.flow.ReasonCode;

public class ModelFailure extends RuntimeException {
    private final ReasonCode reasonCode;

    public ModelFailure(ReasonCode reasonCode) {
        super(reasonCode.name());
        this.reasonCode = reasonCode;
    }

    public ReasonCode reasonCode() {
        return reasonCode;
    }
}
