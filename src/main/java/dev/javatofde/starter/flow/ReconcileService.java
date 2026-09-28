package dev.javatofde.starter.flow;

import dev.javatofde.starter.api.ReconcileRequest;
import dev.javatofde.starter.api.ReconcileResponse;
import dev.javatofde.starter.domain.Invoice;
import dev.javatofde.starter.domain.PurchaseOrder;
import dev.javatofde.starter.domain.ReconciliationDataSource;
import dev.javatofde.starter.model.ModelFailure;
import dev.javatofde.starter.model.Proposal;
import dev.javatofde.starter.model.ProposalModel;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReconcileService {
    private static final Logger log = LoggerFactory.getLogger(ReconcileService.class);
    private static final Pattern INVOICE_ID = Pattern.compile("(?<![A-Za-z0-9_-])INV-[0-9]{4}(?![A-Za-z0-9_-])");

    private final ReconciliationDataSource repository;
    private final ProposalModel model;
    private final BusinessGuard guard;

    public ReconcileService(ReconciliationDataSource repository, ProposalModel model, BusinessGuard guard) {
        this.repository = repository;
        this.model = model;
        this.guard = guard;
    }

    public ReconcileResponse reconcile(ReconcileRequest request) {
        long start = System.nanoTime();
        String traceId = UUID.randomUUID().toString();
        FlowTrace trace = new FlowTrace();
        Matcher matcher = INVOICE_ID.matcher(request.query());
        if (!matcher.find()) {
            trace.moveTo(FlowState.FALLBACK);
            return response(traceId, null, GuardVerdict.review(ReasonCode.INVOICE_ID_NOT_FOUND,
                    "Provide one invoice ID in the form INV-1001."), trace, start, false);
        }
        String invoiceId = matcher.group();
        if (matcher.find()) {
            trace.moveTo(FlowState.FALLBACK);
            return response(traceId, null, GuardVerdict.review(ReasonCode.AMBIGUOUS_INVOICE_ID,
                    "Provide exactly one invoice ID."), trace, start, false);
        }
        trace.moveTo(FlowState.INPUT_VALIDATED);

        Invoice invoice = repository.findInvoice(request.tenantId(), invoiceId).orElse(null);
        if (invoice == null) {
            trace.moveTo(FlowState.FALLBACK);
            return response(traceId, invoiceId, GuardVerdict.review(ReasonCode.INVOICE_NOT_FOUND,
                    "Invoice was not found in this tenant."), trace, start, false);
        }
        PurchaseOrder order = repository.findPurchaseOrder(request.tenantId(), invoice.purchaseOrderId())
                .orElse(null);
        trace.moveTo(FlowState.DATA_LOADED);

        Proposal proposal;
        try {
            proposal = model.propose(request.query(), invoice, order, request.effectiveFault());
        } catch (ModelFailure e) {
            trace.moveTo(FlowState.FALLBACK);
            return response(traceId, invoiceId, GuardVerdict.review(e.reasonCode(),
                    "Model proposal failed; a human must review this invoice."), trace, start, false);
        } catch (RuntimeException e) {
            trace.moveTo(FlowState.FALLBACK);
            return response(traceId, invoiceId, GuardVerdict.review(ReasonCode.MODEL_UNAVAILABLE,
                    "Model proposal failed; a human must review this invoice."), trace, start, false);
        }
        if (proposal == null || !invoiceId.equals(proposal.invoiceId()) || proposal.decision() == null) {
            trace.moveTo(FlowState.FALLBACK);
            return response(traceId, invoiceId, GuardVerdict.review(ReasonCode.MODEL_INVALID_RESPONSE,
                    "Model proposal did not match the requested invoice."), trace, start, false);
        }
        trace.moveTo(FlowState.MODEL_PROPOSED);

        GuardVerdict verdict = guard.check(invoice, order);
        boolean overridden = proposal.decision() == Decision.READY_FOR_REVIEW
                && verdict.decision() == Decision.MANUAL_REVIEW;
        if (proposal.decision() == Decision.MANUAL_REVIEW && verdict.decision() == Decision.READY_FOR_REVIEW) {
            verdict = GuardVerdict.review(ReasonCode.MODEL_REVIEW_REQUIRED,
                    "Model requested human review; the system will not auto-clear it.");
        }
        trace.moveTo(FlowState.GUARDED);
        trace.moveTo(FlowState.COMPLETED);
        return response(traceId, invoiceId, verdict, trace, start, overridden);
    }

    private ReconcileResponse response(String traceId, String invoiceId, GuardVerdict verdict,
                                       FlowTrace trace, long start, boolean overridden) {
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        ReconcileResponse result = new ReconcileResponse(traceId, invoiceId, verdict.decision(), verdict.reasonCode(),
                verdict.reason(), trace.states(), durationMs, overridden);
        log.info("reconcile traceId={} invoiceId={} decision={} reasonCode={} durationMs={}",
                traceId, invoiceId, result.decision(), result.reasonCode(), durationMs);
        return result;
    }
}
