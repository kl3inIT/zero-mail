package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.event.CatalogChangedEvent;
import com.zeromail.core.admin.cat.persistence.lowlevel.CatalogSyncJobRepository;
import com.zeromail.core.admin.cat.projection.CatalogDiff;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import com.zeromail.core.admin.cat.projection.CatalogSyncJob;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogSyncOrchestrator {

    private static final Duration DEBOUNCE_TTL = Duration.ofSeconds(60);

    private final CatalogSyncJobRepository catalogSyncJobRepository;
    private final CatalogAdminService catalogAdminService;
    private final AdminAuditWriter adminAuditWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Optional<StringRedisTemplate> redisTemplate;
    private final Clock clock;

    public CatalogSyncOrchestrator(
            CatalogSyncJobRepository catalogSyncJobRepository,
            CatalogAdminService catalogAdminService,
            AdminAuditWriter adminAuditWriter,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            Clock clock) {
        this.catalogSyncJobRepository =
                Objects.requireNonNull(catalogSyncJobRepository, "catalogSyncJobRepository");
        this.catalogAdminService =
                Objects.requireNonNull(catalogAdminService, "catalogAdminService");
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
        this.applicationEventPublisher =
                Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher");
        this.redisTemplate = Optional.ofNullable(redisTemplateProvider.getIfAvailable());
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public CatalogSyncFetchResult startFetch(
            LlmProvider provider, String requestIp, UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        if (LlmProvider.ANTHROPIC.equals(provider)) {
            throw new CatalogSyncAnthropicDisabledException();
        }
        UUID candidateJobId = UUID.randomUUID();
        Optional<UUID> redisDebouncedJobId = acquireDebounce(provider, candidateJobId);
        if (redisDebouncedJobId.isPresent()) {
            return new CatalogSyncFetchResult(redisDebouncedJobId.get(), "IN_PROGRESS");
        }
        Optional<UUID> recentJobId =
                catalogSyncJobRepository.findActiveJobWithin(
                        provider, clock.instant().minus(DEBOUNCE_TTL));
        if (recentJobId.isPresent()) {
            releaseDebounce(provider);
            return new CatalogSyncFetchResult(recentJobId.get(), "IN_PROGRESS");
        }
        catalogSyncJobRepository.insertFetchJob(candidateJobId, provider, adminUser.id());
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_SYNC_FETCH_STARTED,
                "processing_job",
                candidateJobId,
                null,
                Map.of("provider", provider.id(), "job_id", candidateJobId.toString()),
                "Catalog sync fetch started",
                requestIp,
                requestId);
        return new CatalogSyncFetchResult(candidateJobId, "IN_PROGRESS");
    }

    @Transactional(readOnly = true)
    public CatalogDiff diff(UUID jobId) {
        return catalogSyncJobRepository.diffForJob(jobId);
    }

    @Transactional(readOnly = true)
    public CatalogSyncDiffResult diffResult(UUID jobId) {
        CatalogSyncJob catalogSyncJob =
                catalogSyncJobRepository
                        .findJob(jobId)
                        .orElseThrow(
                                () ->
                                        new CatalogSyncJobRepository
                                                .CatalogSyncJobNotFoundException(jobId));
        String step = catalogSyncJob.stepStateJson().path("step").asString();
        String responseStatus = mapStepToResponseStatus(step);
        CatalogDiff catalogDiff =
                "DIFF_READY".equals(step)
                        ? catalogSyncJobRepository.diffForJob(jobId)
                        : CatalogDiff.empty();
        return new CatalogSyncDiffResult(jobId, responseStatus, catalogDiff);
    }

    private static String mapStepToResponseStatus(String step) {
        if (step == null) {
            return "IN_PROGRESS";
        }
        return switch (step) {
            case "DIFF_READY" -> "AWAITING_CONFIRM";
            case "CONFIRMED" -> "CONFIRMED";
            case "CANCELLED" -> "CANCELLED";
            case "FAILED", "ABANDONED" -> "FAILED";
            default -> "IN_PROGRESS";
        };
    }

    @Transactional
    public CatalogChangedEvent confirm(
            UUID jobId, String reason, String requestIp, UUID requestId) {
        AdminUser confirmer = AdminContext.currentOrThrow();
        CatalogSyncJob catalogSyncJob =
                catalogSyncJobRepository
                        .findJob(jobId)
                        .orElseThrow(
                                () ->
                                        new CatalogSyncJobRepository
                                                .CatalogSyncJobNotFoundException(jobId));
        if (!"DIFF_READY".equals(catalogSyncJob.stepStateJson().path("step").asString())) {
            throw new CatalogSyncNotReadyException();
        }
        CatalogDiff catalogDiff = catalogSyncJobRepository.diffForJob(jobId);
        List<String> affectedModelIds = new ArrayList<>();
        for (CatalogModelRow row : catalogDiff.added()) {
            catalogAdminService.upsertFetchedModel(catalogSyncJob.provider(), row);
            affectedModelIds.add(row.modelId());
        }
        for (CatalogModelRow row : catalogDiff.changed()) {
            catalogAdminService.upsertFetchedModel(catalogSyncJob.provider(), row);
            affectedModelIds.add(row.modelId());
        }
        for (CatalogModelRow row : catalogDiff.removed()) {
            catalogAdminService.softDeleteFetchedModel(row.modelId());
            affectedModelIds.add(row.modelId());
        }
        long catalogVersion = catalogAdminService.bumpProviderVersion(catalogSyncJob.provider());
        catalogSyncJobRepository.markConfirmed(jobId);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_SYNC_CONFIRMED,
                "processing_job",
                jobId,
                null,
                Map.of(
                        "provider",
                        catalogSyncJob.provider().id(),
                        "job_id",
                        jobId.toString(),
                        "confirmer_id",
                        confirmer.id().toString()),
                reason == null || reason.isBlank() ? "Catalog sync confirmed" : reason,
                requestIp,
                requestId);
        releaseDebounce(catalogSyncJob.provider());
        CatalogChangedEvent event =
                new CatalogChangedEvent(
                        catalogSyncJob.provider(),
                        List.copyOf(affectedModelIds),
                        EnumSet.allOf(Feature.class),
                        clock.instant(),
                        catalogVersion);
        applicationEventPublisher.publishEvent(event);
        return event;
    }

    @Transactional
    public void cancel(UUID jobId, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        CatalogSyncJob catalogSyncJob =
                catalogSyncJobRepository
                        .findJob(jobId)
                        .orElseThrow(
                                () ->
                                        new CatalogSyncJobRepository
                                                .CatalogSyncJobNotFoundException(jobId));
        catalogSyncJobRepository.markCancelled(jobId);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_SYNC_CANCELLED,
                "processing_job",
                jobId,
                null,
                Map.of("provider", catalogSyncJob.provider().id(), "job_id", jobId.toString()),
                "Catalog sync cancelled",
                requestIp,
                requestId);
        releaseDebounce(catalogSyncJob.provider());
    }

    private Optional<UUID> acquireDebounce(LlmProvider provider, UUID candidateJobId) {
        if (redisTemplate.isEmpty()) {
            return Optional.empty();
        }
        String key = debounceKey(provider);
        Boolean acquired =
                redisTemplate
                        .get()
                        .opsForValue()
                        .setIfAbsent(key, candidateJobId.toString(), DEBOUNCE_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        String existingJobId = redisTemplate.get().opsForValue().get(key);
        if (existingJobId == null || existingJobId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(existingJobId));
    }

    void releaseDebounce(LlmProvider provider) {
        redisTemplate.ifPresent(template -> template.delete(debounceKey(provider)));
    }

    private static String debounceKey(LlmProvider provider) {
        return "zeromail:catalog:sync:debounce:" + provider.id();
    }

    public record CatalogSyncFetchResult(UUID jobId, String status) {}

    public record CatalogSyncDiffResult(UUID jobId, String status, CatalogDiff catalogDiff) {}

    public static class CatalogSyncAnthropicDisabledException extends AdminBusinessException {
        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.catalog_sync_anthropic_disabled";
        }

        @Override
        public String logEvent() {
            return "admin_catalog_sync_anthropic_disabled";
        }

        @Override
        public String detail() {
            return "Anthropic catalog sync is disabled for the current provider configuration.";
        }
    }

    public static class CatalogSyncNotReadyException extends AdminBusinessException {
        @Override
        public ErrorClass errorClass() {
            return ErrorClass.CONFLICT;
        }

        @Override
        public String errorCode() {
            return "error.admin.catalog_sync_not_ready";
        }

        @Override
        public String logEvent() {
            return "admin_catalog_sync_not_ready";
        }

        @Override
        public String detail() {
            return "The catalog sync job is not in a state that allows this action.";
        }
    }
}
