/**
 * Cross-cutting persistence base classes: 3-tier abstract entity hierarchy ({@link
 * com.zeromail.core.shared.persistence.AbstractEntity} → {@link
 * com.zeromail.core.shared.persistence.AbstractAuditableEntity} → {@link
 * com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity}).
 *
 * <p><b>Phase 1.2.1 entity hierarchy (D-A1):</b>
 *
 * <pre>
 *   AbstractEntity                 (id only)            ← TenantEntity
 *     └─ AbstractAuditableEntity   (+ createdAt, updatedAt, version)
 *          └─ AbstractTenantOwnedEntity  (+ @TenantId tenantId)
 *               ├─ UserEntity
 *               ├─ OnboardingSelectionEntity
 *               └─ GmailConnectionEntity
 * </pre>
 *
 * <p><b>Audit population (D-A4):</b> Spring Data JPA {@code AuditingEntityListener} populates
 * {@code @CreatedDate} / {@code @LastModifiedDate} from the {@code DateTimeProvider} bean named
 * {@code "appDateTimeProvider"}, which delegates to the project {@code Clock} bean (production:
 * {@code Clock.systemUTC()}; tests override with {@code Clock.fixed(...)}). The bean is provided by
 * the API module's {@code JpaAuditingConfig}.
 *
 * <p><b>D-A5 — bulk SQL bypass (CRITICAL):</b> {@code @LastModifiedDate} and {@code @PreUpdate}
 * lifecycle callbacks DO NOT fire for bulk JPQL {@code @Modifying @Query}, native SQL via {@code
 * entityManager.createNativeQuery(...)}, or {@code JdbcTemplate} updates. If a future bulk update
 * path is added, set {@code updated_at = NOW()} explicitly in the query, or migrate to a DB trigger
 * for that specific table. {@code OnboardingService.deleteByTenantId} (Plan 03 WR-03 closure) is
 * unaffected because DELETE has no audit semantics.
 *
 * <p><b>Privacy (D-A4):</b> {@code @CreatedBy} / {@code @LastModifiedBy} are deliberately omitted.
 * Row-level user identity columns are forbidden until the Phase 6 audit-log table lands with a
 * proper {@code AuditorAware<UUID>} bean reading from Spring Security.
 *
 * <p><b>Spring Modulith naming form (CL-3 lock):</b> Cross-sibling modules MUST reference this
 * module as {@code "shared :: persistence"} in their {@code allowedDependencies} array.
 *
 * <p><b>Design rationale:</b> Adapted from JHipster {@code AbstractAuditingEntity<T>} (single
 * class, generic ID, includes {@code createdBy}/{@code lastModifiedBy}) — we split into 3 tiers and
 * dropped identity columns per the project privacy constraint.
 */
@NamedInterface("persistence")
package com.zeromail.core.shared.persistence;

import org.springframework.modulith.NamedInterface;
