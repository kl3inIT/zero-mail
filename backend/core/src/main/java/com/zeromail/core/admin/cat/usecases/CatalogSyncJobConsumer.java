package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.cat.projection.CatalogDiff;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import com.zeromail.core.admin.cat.projection.CatalogSyncJob;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.llm.gateway.springai.admin.ModelsProbeClient;
import com.zeromail.core.llm.gateway.springai.admin.ProviderMasterKeyResolver;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CatalogSyncJobConsumer {

    private static final Duration LOCK_DURATION = Duration.ofSeconds(60);

    private final CatalogSyncJobRepository catalogSyncJobRepository;
    private final ProviderMasterKeyResolver providerMasterKeyResolver;
    private final ModelsProbeClient modelsProbeClient;
    private final ModelSchemaValidator modelSchemaValidator;
    private final JdbcTemplate jdbcTemplate;

    public CatalogSyncJobConsumer(
            CatalogSyncJobRepository catalogSyncJobRepository,
            ProviderMasterKeyResolver providerMasterKeyResolver,
            ModelsProbeClient modelsProbeClient,
            ModelSchemaValidator modelSchemaValidator,
            JdbcTemplate jdbcTemplate) {
        this.catalogSyncJobRepository =
                Objects.requireNonNull(catalogSyncJobRepository, "catalogSyncJobRepository");
        this.providerMasterKeyResolver =
                Objects.requireNonNull(providerMasterKeyResolver, "providerMasterKeyResolver");
        this.modelsProbeClient = Objects.requireNonNull(modelsProbeClient, "modelsProbeClient");
        this.modelSchemaValidator =
                Objects.requireNonNull(modelSchemaValidator, "modelSchemaValidator");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Scheduled(fixedDelay = 2000)
    public void drainOnce() {
        processNext();
    }

    @Transactional
    public boolean processNext() {
        return catalogSyncJobRepository
                .claimNextFetchJob(LOCK_DURATION)
                .map(
                        catalogSyncJob -> {
                            process(catalogSyncJob);
                            return true;
                        })
                .orElse(false);
    }

    private void process(CatalogSyncJob catalogSyncJob) {
        try {
            ProviderMasterKeyResolver.ResolvedMasterKey resolvedMasterKey =
                    providerMasterKeyResolver.resolve(catalogSyncJob.provider());
            List<ModelsProbeClient.RawModel> rawModels =
                    modelsProbeClient.fetchModelCatalog(
                            catalogSyncJob.provider(),
                            resolvedMasterKey.keyFormat(),
                            resolvedMasterKey.baseUrl(),
                            resolvedMasterKey.plaintextKey());
            modelSchemaValidator.validateRawModels(catalogSyncJob.provider(), rawModels);
            CatalogDiff catalogDiff = computeDiff(catalogSyncJob.provider(), rawModels);
            catalogSyncJobRepository.markDiffReady(catalogSyncJob.jobId(), catalogDiff);
        } catch (ModelsProbeClient.ProbeFailedException probeFailedException) {
            catalogSyncJobRepository.markFailed(
                    catalogSyncJob.jobId(), probeFailedException.reason().name());
        } catch (RuntimeException runtimeException) {
            catalogSyncJobRepository.markFailed(catalogSyncJob.jobId(), "UNKNOWN");
        }
    }

    private CatalogDiff computeDiff(
            LlmProvider provider, List<ModelsProbeClient.RawModel> rawModels) {
        Map<String, ExistingModel> existingModels = existingModels(provider);
        Map<String, ModelsProbeClient.RawModel> fetchedModels = new HashMap<>();
        for (ModelsProbeClient.RawModel rawModel : rawModels) {
            fetchedModels.put(rawModel.modelId(), rawModel);
        }
        List<CatalogModelRow> added =
                rawModels.stream()
                        .filter(rawModel -> !existingModels.containsKey(rawModel.modelId()))
                        .map(rawModel -> toCatalogRow(provider, rawModel))
                        .sorted(Comparator.comparing(CatalogModelRow::modelId))
                        .toList();
        List<CatalogModelRow> changed =
                rawModels.stream()
                        .filter(rawModel -> existingModels.containsKey(rawModel.modelId()))
                        .filter(
                                rawModel ->
                                        rawModel.displayName() != null
                                                && !rawModel.displayName()
                                                        .equals(
                                                                existingModels
                                                                        .get(rawModel.modelId())
                                                                        .displayName()))
                        .map(rawModel -> toCatalogRow(provider, rawModel))
                        .sorted(Comparator.comparing(CatalogModelRow::modelId))
                        .toList();
        List<CatalogModelRow> removed =
                existingModels.values().stream()
                        .filter(
                                existingModel ->
                                        !fetchedModels.containsKey(existingModel.modelId()))
                        .map(
                                existingModel ->
                                        new CatalogModelRow(
                                                provider.id(),
                                                existingModel.modelId(),
                                                existingModel.displayName(),
                                                false,
                                                false,
                                                null,
                                                null,
                                                null,
                                                0L))
                        .sorted(Comparator.comparing(CatalogModelRow::modelId))
                        .toList();
        return new CatalogDiff(added, removed, changed);
    }

    private Map<String, ExistingModel> existingModels(LlmProvider provider) {
        List<ExistingModel> rows =
                jdbcTemplate.query(
                        """
                        SELECT model_id, display_name
                        FROM model_catalog
                        WHERE provider = ?
                          AND deprecated_at IS NULL
                        """,
                        (resultSet, rowNumber) ->
                                new ExistingModel(
                                        resultSet.getString("model_id"),
                                        resultSet.getString("display_name")),
                        provider.id());
        Map<String, ExistingModel> modelsById = new HashMap<>();
        for (ExistingModel row : rows) {
            modelsById.put(row.modelId(), row);
        }
        return modelsById;
    }

    private static CatalogModelRow toCatalogRow(
            LlmProvider provider, ModelsProbeClient.RawModel rawModel) {
        String displayName =
                rawModel.displayName() == null || rawModel.displayName().isBlank()
                        ? rawModel.modelId()
                        : rawModel.displayName();
        return new CatalogModelRow(
                provider.id(), rawModel.modelId(), displayName, false, false, null, null, null, 0L);
    }

    private record ExistingModel(String modelId, String displayName) {}
}
