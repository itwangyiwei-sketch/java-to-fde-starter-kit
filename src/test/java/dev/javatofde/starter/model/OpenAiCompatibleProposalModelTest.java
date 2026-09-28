package dev.javatofde.starter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.javatofde.starter.domain.FixtureRepository;
import dev.javatofde.starter.domain.Invoice;
import dev.javatofde.starter.domain.PurchaseOrder;
import dev.javatofde.starter.flow.Decision;
import dev.javatofde.starter.flow.ReasonCode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleProposalModelTest {
    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>(
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"invoiceId\\\":\\\"INV-1001\\\",\\\"decision\\\":\\\"READY_FOR_REVIEW\\\"}\"}}]}");

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void parsesAValidProposal() {
        Proposal proposal = model().propose("核对 INV-1001", invoice(), order(), DemoFault.NONE);

        assertThat(proposal.invoiceId()).isEqualTo("INV-1001");
        assertThat(proposal.decision()).isEqualTo(Decision.READY_FOR_REVIEW);
    }

    @Test
    void rejectsMalformedModelOutput() {
        body.set("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}");

        assertThatThrownBy(() -> model().propose("核对 INV-1001", invoice(), order(), DemoFault.NONE))
                .isInstanceOfSatisfying(ModelFailure.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo(ReasonCode.MODEL_INVALID_RESPONSE));
    }

    @Test
    void rejectsEmptyProviderBodyAsInvalidOutput() {
        body.set("");

        assertThatThrownBy(() -> model().propose("核对 INV-1001", invoice(), order(), DemoFault.NONE))
                .isInstanceOfSatisfying(ModelFailure.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo(ReasonCode.MODEL_INVALID_RESPONSE));
    }

    @Test
    void rejectsEmptyMessageAsInvalidOutput() {
        body.set("{\"choices\":[{\"message\":{\"content\":\"\"}}]}");

        assertThatThrownBy(() -> model().propose("核对 INV-1001", invoice(), order(), DemoFault.NONE))
                .isInstanceOfSatisfying(ModelFailure.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo(ReasonCode.MODEL_INVALID_RESPONSE));
    }

    @Test
    void failsClosedOnProviderError() {
        status.set(503);

        assertThatThrownBy(() -> model().propose("核对 INV-1001", invoice(), order(), DemoFault.NONE))
                .isInstanceOfSatisfying(ModelFailure.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo(ReasonCode.MODEL_UNAVAILABLE));
    }

    private OpenAiCompatibleProposalModel model() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        return new OpenAiCompatibleProposalModel(new ObjectMapper(), baseUrl, "fixture-model", "test-key", 1000);
    }

    private Invoice invoice() {
        return new FixtureRepository().findInvoice("acme", "INV-1001").orElseThrow();
    }

    private PurchaseOrder order() {
        return new FixtureRepository().findPurchaseOrder("acme", "PO-1001").orElseThrow();
    }
}
