package dev.javatofde.starter.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.javatofde.starter.api.ReconcileRequest;
import dev.javatofde.starter.api.ReconcileResponse;
import dev.javatofde.starter.domain.FixtureRepository;
import dev.javatofde.starter.model.DemoFault;
import dev.javatofde.starter.model.MockProposalModel;
import dev.javatofde.starter.model.Proposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReconcileServiceTest {
    private final FixtureRepository repository = new FixtureRepository();
    private final BusinessGuard guard = new BusinessGuard();

    @Test
    void matchedInvoiceNeedsHumanApproval() {
        ReconcileResponse result = service().reconcile(request("acme", "核对 INV-1001", DemoFault.NONE));

        assertThat(result.decision()).isEqualTo(Decision.READY_FOR_REVIEW);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.MATCHED);
        assertThat(result.states()).containsExactly(FlowState.RECEIVED, FlowState.INPUT_VALIDATED,
                FlowState.DATA_LOADED, FlowState.MODEL_PROPOSED, FlowState.GUARDED, FlowState.COMPLETED);
    }

    @Test
    void guardRejectsModelOverapproval() {
        ReconcileResponse result = service().reconcile(request("acme", "核对 INV-1002", DemoFault.OVERAPPROVE));

        assertThat(result.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.AMOUNT_MISMATCH);
        assertThat(result.guardOverrodeModel()).isTrue();
    }

    @Test
    void modelTimeoutFallsBackToHuman() {
        ReconcileResponse result = service().reconcile(request("acme", "核对 INV-1001", DemoFault.TIMEOUT));

        assertThat(result.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.MODEL_TIMEOUT);
        assertThat(result.states().get(result.states().size() - 1)).isEqualTo(FlowState.FALLBACK);
    }

    @Test
    void crossTenantInvoiceDoesNotRevealRecord() {
        ReconcileResponse result = service().reconcile(request("acme", "核对 INV-2001", DemoFault.NONE));

        assertThat(result.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.INVOICE_NOT_FOUND);
        assertThat(result.states()).doesNotContain(FlowState.DATA_LOADED);
    }

    @Test
    void modelCannotSwitchInvoiceId() {
        ReconcileService maliciousModel = new ReconcileService(repository,
                (query, invoice, order, fault) -> new Proposal("INV-2001", Decision.READY_FOR_REVIEW), guard);
        ReconcileResponse result = maliciousModel.reconcile(request("acme", "核对 INV-1001", DemoFault.NONE));

        assertThat(result.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.MODEL_INVALID_RESPONSE);
        assertThat(result.states().get(result.states().size() - 1)).isEqualTo(FlowState.FALLBACK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"核对 INV-1001A", "核对 xINV-1001", "核对 INV-1001-2"})
    void malformedIdsCannotAliasKnownInvoice(String query) {
        ReconcileResponse result = service().reconcile(request("acme", query, DemoFault.NONE));

        assertThat(result.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.INVOICE_ID_NOT_FOUND);
        assertThat(result.states().get(result.states().size() - 1)).isEqualTo(FlowState.FALLBACK);
    }

    @Test
    void stateMachineRejectsUnplannedJump() {
        FlowTrace trace = new FlowTrace();
        assertThatThrownBy(() -> trace.moveTo(FlowState.COMPLETED))
                .isInstanceOf(IllegalStateException.class);
    }

    private ReconcileService service() {
        return new ReconcileService(repository, new MockProposalModel(), guard);
    }

    private ReconcileRequest request(String tenant, String query, DemoFault fault) {
        return new ReconcileRequest(tenant, query, fault);
    }
}
