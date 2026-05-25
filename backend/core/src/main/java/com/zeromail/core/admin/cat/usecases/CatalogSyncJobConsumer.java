package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import com.zeromail.core.admin.cat.persistence.lowlevel.CatalogSyncJobRepository;
import com.zeromail.core.admin.cat.persistence.lowlevel.ModelCatalogWriteRepository;
import com.zeromail.core.admin.cat.projection.CatalogDiff;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import com.zeromail.core.admin.cat.projection.CatalogSyncJob;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.usecases.ModelsProbeClient;
import com.zeromail.core.admin.mkey.usecases.ProviderMasterKeyResolver;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ModelCatalogWriteRepository modelCatalogWriteRepository;

    public CatalogSyncJobConsumer(
            CatalogSyncJobRepository catalogSyncJobRepository,
            ProviderMasterKeyResolver providerMasterKeyResolver,
            ModelsProbeClient modelsProbeClient,
            ModelSchemaValidator modelSchemaValidator,
            ModelCatalogWriteRepository modelCatalogWriteRepository) {
        this.catalogSyncJobRepository =
                Objects.requireNonNull(catalogSyncJobRepository, "catalogSyncJobRepository");
        this.providerMasterKeyResolver =
                Objects.requireNonNull(providerMasterKeyResolver, "providerMasterKeyResolver");
        this.modelsProbeClient = Objects.requireNonNull(modelsProbeClient, "modelsProbeClient");
        this.modelSchemaValidator =
                Objects.requireNonNull(modelSchemaValidator, "modelSchemaValidator");
        this.modelCatalogWriteRepository =
                Objects.requireNonNull(
                        modelCatalogWriteRepository,
                        "modelCatalogWriteRepository must not be null");
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
                                                ModelVerificationStatus.UNTESTED,
                                                null,
                                                0L))
                        .sorted(Comparator.comparing(CatalogModelRow::modelId))
                        .toList();
        return new CatalogDiff(added, removed, changed);
    }

    private Map<String, ExistingModel> existingModels(LlmProvider provider) {
        Map<String, String> displayNamesById =
                modelCatalogWriteRepository.findActiveModelDisplayNames(provider);
        Map<String, ExistingModel> modelsById = new java.util.HashMap<>();
        displayNamesById.forEach(
                (modelId, displayName) ->
                        modelsById.put(modelId, new ExistingModel(modelId, displayName)));
        return modelsById;
    }

    private static CatalogModelRow toCatalogRow(
            LlmProvider provider, ModelsProbeClient.RawModel rawModel) {
        String displayName =
                rawModel.displayName() == null || rawModel.displayName().isBlank()
                        ? rawModel.modelId()
                        : rawModel.displayName();
        return new CatalogModelRow(
                provider.id(),
                rawModel.modelId(),
                displayName,
                false,
                false,
                null,
                null,
                ModelVerificationStatus.UNTESTED,
                null,
                0L);
    }

    private record ExistingModel(String modelId, String displayName) {}
}
