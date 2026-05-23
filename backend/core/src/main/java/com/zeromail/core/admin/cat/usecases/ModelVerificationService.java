package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import com.zeromail.core.admin.cat.persistence.ModelCatalogEntity;
import com.zeromail.core.admin.cat.persistence.ModelCatalogRepository;
import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import com.zeromail.core.admin.mkey.usecases.ModelsProbeClient;
import com.zeromail.core.admin.mkey.usecases.ProviderMasterKeyResolver;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Probes a single (provider, model) pair against the platform's active master key and records the
 * result on the model_catalog row.
 *
 * <p>Phase B v2 introduces explicit verification — only VERIFIED (or STALE) models are eligible for
 * the 3-tier router. This service is the single write path for {@code
 * model_catalog.verification_status}: any other code that mutates that column will drift from the
 * audit log.
 *
 * <p>The actual probe currently delegates to {@link ModelsProbeClient}'s key probe (GET /models). A
 * model-specific chat-completion probe will replace this once the Spring AI streaming adapter
 * exposes a per-model hook; until then a successful key probe is taken as proof that the model is
 * reachable under the current credentials.
 */
@Service
public class ModelVerificationService {

    /** STALE threshold — VERIFIED rows older than this turn STALE on the next probe pass. */
    public static final Duration FRESHNESS_WINDOW = Duration.ofDays(7);

    private final ModelCatalogRepository modelCatalogRepository;
    private final ProviderMasterKeyResolver providerMasterKeyResolver;
    private final ModelsProbeClient modelsProbeClient;
    private final AdminAuditWriter adminAuditWriter;
    private final Clock clock;

    public ModelVerificationService(
            ModelCatalogRepository modelCatalogRepository,
            ProviderMasterKeyResolver providerMasterKeyResolver,
            ModelsProbeClient modelsProbeClient,
            AdminAuditWriter adminAuditWriter,
            Clock clock) {
        this.modelCatalogRepository =
                Objects.requireNonNull(modelCatalogRepository, "modelCatalogRepository");
        this.providerMasterKeyResolver =
                Objects.requireNonNull(providerMasterKeyResolver, "providerMasterKeyResolver");
        this.modelsProbeClient = Objects.requireNonNull(modelsProbeClient, "modelsProbeClient");
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public ModelVerificationStatus verify(String modelId, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        ModelCatalogEntity modelRow =
                modelCatalogRepository
                        .findById(modelId)
                        .orElseThrow(() -> new ModelNotFoundException(modelId));

        ProviderMasterKeyResolver.ResolvedMasterKey resolvedKey;
        try {
            resolvedKey = providerMasterKeyResolver.resolve(modelRow.getProvider());
        } catch (ProviderMasterKeyResolver.MissingMasterKeyException missingMasterKeyException) {
            return recordOutcome(
                    modelRow,
                    ModelVerificationStatus.FAILED,
                    null,
                    null,
                    "Provider has no active master key",
                    requestIp,
                    requestId,
                    null);
        }

        Instant probeStartedAt = clock.instant();
        // We intentionally do not wipe the plaintext here — the resolver hands out a defensive copy
        // in ResolvedMasterKey, and the cache manages its own lifetime. The plaintext goes out of
        // scope when resolvedKey does.
        MasterKeyTestResult probeResult =
                modelsProbeClient.probe(
                        modelRow.getProvider(),
                        resolvedKey.keyFormat(),
                        resolvedKey.baseUrl(),
                        resolvedKey.plaintextKey());
        Instant probeFinishedAt = clock.instant();
        int latencyMs = (int) Duration.between(probeStartedAt, probeFinishedAt).toMillis();

        ModelVerificationStatus outcome =
                probeResult == MasterKeyTestResult.OK
                        ? ModelVerificationStatus.VERIFIED
                        : ModelVerificationStatus.FAILED;
        String errorMessage = probeResult == MasterKeyTestResult.OK ? null : probeResult.name();
        return recordOutcome(
                modelRow,
                outcome,
                probeFinishedAt,
                latencyMs,
                errorMessage,
                requestIp,
                requestId,
                probeResult);
    }

    /**
     * Marks models that have not been re-probed within the freshness window as STALE. STALE rows
     * remain routable but operators get a visible warning.
     */
    @Transactional
    public int markStaleVerifiedRows() {
        Instant cutoff = clock.instant().minus(FRESHNESS_WINDOW);
        int marked = 0;
        for (ModelCatalogEntity modelRow : modelCatalogRepository.findAll()) {
            if (modelRow.getVerificationStatus() != ModelVerificationStatus.VERIFIED) continue;
            if (modelRow.getLastTestAt() == null) continue;
            if (modelRow.getLastTestAt().isAfter(cutoff)) continue;
            modelRow.recordTestResult(
                    ModelVerificationStatus.STALE,
                    modelRow.getLastTestAt(),
                    modelRow.getLastTestLatencyMs(),
                    modelRow.getLastTestCostMicros(),
                    modelRow.getLastTestError());
            marked++;
        }
        return marked;
    }

    private ModelVerificationStatus recordOutcome(
            ModelCatalogEntity modelRow,
            ModelVerificationStatus outcome,
            Instant testedAt,
            Integer latencyMs,
            String error,
            String requestIp,
            UUID requestId,
            MasterKeyTestResult probeResult) {
        Instant now = testedAt != null ? testedAt : clock.instant();
        modelRow.recordTestResult(outcome, now, latencyMs, null, error);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_MODEL_VERIFICATION_RECORDED,
                "model_catalog",
                null,
                (Map<String, ?>) null,
                Map.of(
                        "provider", modelRow.getProvider().id(),
                        "model_id", modelRow.getModelId(),
                        "outcome", outcome.id(),
                        "probe_result", probeResult == null ? "MISSING_KEY" : probeResult.name(),
                        "latency_ms", latencyMs == null ? "" : Integer.toString(latencyMs)),
                null,
                requestIp,
                requestId);
        return outcome;
    }

    public static final class ModelNotFoundException extends AdminBusinessException {

        public ModelNotFoundException(String modelId) {
            super("Catalog model not found: " + modelId);
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.NOT_FOUND;
        }

        @Override
        public String errorCode() {
            return "error.admin.catalog_model_not_found";
        }

        @Override
        public String logEvent() {
            return "admin_catalog_model_not_found";
        }

        @Override
        public String detail() {
            return "Model not found in catalog.";
        }
    }
}
