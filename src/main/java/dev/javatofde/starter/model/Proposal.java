package dev.javatofde.starter.model;

import dev.javatofde.starter.flow.Decision;

public record Proposal(String invoiceId, Decision decision) {
}
