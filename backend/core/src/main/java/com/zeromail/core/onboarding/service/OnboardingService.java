package com.zeromail.core.onboarding.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.account.model.CurrentUserNotFoundException;
import com.zeromail.core.account.service.AccountService;
import com.zeromail.core.onboarding.model.OnboardingStep;
import com.zeromail.core.onboarding.persistence.OnboardingSelectionEntity;
import com.zeromail.core.onboarding.persistence.OnboardingSelectionRepository;
import com.zeromail.core.tenant.TenantContext;

/**
 * Onboarding state-machine application service.
 *
 * <p>Both {@link #selectTemplate(UUID, String)} and {@link #complete(UUID)} require a user to exist
 * for the current tenant. If it does not, {@link CurrentUserNotFoundException} propagates from
 * {@link AccountService#advanceOnboardingStep(UUID, OnboardingStep)} rather than silently no-oping
 * (the previous controller-level behavior could leave a tenant with a saved selection but no
 * advanced user state, while the client saw 2xx).
 *
 * <p>Phase 1.2 reshape (D-D1 — enforced by {@code DomainBoundaryArchTests}): the previous
 * incarnation injected {@code UserRepository} (account domain) directly. Cross-domain reads now go
 * through {@link AccountService}; this service holds only its own {@link
 * OnboardingSelectionRepository}.
 */
@Service
public class OnboardingService {

  private final OnboardingSelectionRepository onboardingSelectionRepository;
  private final AccountService accountService;

  public OnboardingService(
      OnboardingSelectionRepository onboardingSelectionRepository, AccountService accountService) {
    this.onboardingSelectionRepository = onboardingSelectionRepository;
    this.accountService = accountService;
  }

  @Transactional
  public void selectTemplate(UUID tenantId, String templateKey) {
    onboardingSelectionRepository.save(
        new OnboardingSelectionEntity(UUID.randomUUID(), tenantId, templateKey));
    accountService.advanceOnboardingStep(tenantId, OnboardingStep.TEMPLATE_SELECTED);
  }

  public List<String> selectedEnabledTemplateKeys(UUID tenantId) {
    try {
      return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
          .call(
              () ->
                  onboardingSelectionRepository.findByTenantId(tenantId).stream()
                      .filter(OnboardingSelectionEntity::isEnabled)
                      .map(OnboardingSelectionEntity::getTemplateKey)
                      .distinct()
                      .sorted()
                      .toList());
    } catch (RuntimeException runtimeFailure) {
      throw runtimeFailure;
    } catch (Exception checkedFailure) {
      throw new IllegalStateException("Selected onboarding templates could not be read", checkedFailure);
    }
  }

  @Transactional
  public void complete(UUID tenantId) {
    accountService.advanceOnboardingStep(tenantId, OnboardingStep.COMPLETE);
  }

  /**
   * Deletes all onboarding selections for the given tenant via a single bulk JPQL DELETE (closes
   * REVIEW WR-03 — previous incarnation issued 1 SELECT + N DELETEs).
   *
   * <p>Single-domain delete only — orchestration of cross-domain cascade lives in {@code
   * AccountDeletionController} per CL-2 + D-D1.
   *
   * <p><b>Caller contract:</b> {@code tenantId} MUST be the resolved current-tenant id from {@code
   * TenantContext}. The bulk JPQL bypasses Hibernate's {@code @TenantId} discriminator filter (D-A5
   * caveat), so the explicit {@code WHERE o.tenantId = :tenantId} in the repository query is the
   * only tenant isolation gate.
   */
  @Transactional
  public void deleteSelectionsForCurrentTenant(UUID tenantId) {
    onboardingSelectionRepository.deleteByTenantId(tenantId);
  }
}
