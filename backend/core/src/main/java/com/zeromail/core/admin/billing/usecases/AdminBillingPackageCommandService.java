package com.zeromail.core.admin.billing.usecases;

import com.zeromail.core.admin.billing.persistence.lowlevel.AdminBillingPackageWriteRepository;
import com.zeromail.core.billing.usecases.FeatureCatalogCache;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AdminBillingPackageCommandService {

    private final AdminBillingPackageWriteRepository adminBillingPackageWriteRepository;
    private final FeatureCatalogCache featureCatalogCache;

    public AdminBillingPackageCommandService(
            AdminBillingPackageWriteRepository adminBillingPackageWriteRepository,
            FeatureCatalogCache featureCatalogCache) {
        this.adminBillingPackageWriteRepository =
                Objects.requireNonNull(
                        adminBillingPackageWriteRepository,
                        "adminBillingPackageWriteRepository must not be null");
        this.featureCatalogCache =
                Objects.requireNonNull(featureCatalogCache, "featureCatalogCache must not be null");
    }

    @Transactional
    public void setPlanFeaturePermissionEnabled(
            String featureCode, String planCode, boolean enabled) {
        if (featureCode == null || featureCode.isBlank()) {
            throw new IllegalArgumentException("featureCode must not be blank");
        }
        if (planCode == null || planCode.isBlank()) {
            throw new IllegalArgumentException("planCode must not be blank");
        }

        boolean permissionUpdated =
                adminBillingPackageWriteRepository.setPlanFeaturePermissionEnabled(
                        featureCode, planCode, enabled);
        if (!permissionUpdated) {
            throw new IllegalArgumentException(
                    "Unknown billing plan or feature catalog entry for permission update");
        }
    }

    @Transactional
    public void setFeatureCreditCost(String featureCode, int fixedCreditCost) {
        if (featureCode == null || featureCode.isBlank()) {
            throw new IllegalArgumentException("featureCode must not be blank");
        }
        if (fixedCreditCost < 0) {
            throw new IllegalArgumentException("fixedCreditCost must not be negative");
        }

        boolean featureUpdated =
                adminBillingPackageWriteRepository.setFeatureCreditCost(
                        featureCode, fixedCreditCost);
        if (!featureUpdated) {
            throw new IllegalArgumentException(
                    "Unknown feature catalog entry for credit cost update");
        }
        refreshFeatureCatalogCacheAfterCommit();
    }

    private void refreshFeatureCatalogCacheAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            featureCatalogCache.refresh();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        featureCatalogCache.refresh();
                    }
                });
    }
}
