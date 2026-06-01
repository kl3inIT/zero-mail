package com.zeromail.core.admin.mkey.persistence;

import com.zeromail.core.admin.mkey.domain.MasterKeyStatus;
import com.zeromail.core.llm.domain.LlmProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmProviderMasterKeyRepository
        extends JpaRepository<LlmProviderMasterKeyEntity, LlmProviderMasterKeyId> {

    /**
     * Phase B v2: list all keys (every priority, every status) ordered by provider then priority.
     * Used by admin list views.
     */
    @Query(
            "select masterKey from LlmProviderMasterKeyEntity masterKey "
                    + "order by masterKey.provider, masterKey.priority")
    List<LlmProviderMasterKeyEntity> findAllOrderedByProviderAndPriority();

    /**
     * Phase B v2: every key for one provider, ordered by priority ascending. Includes REVOKED rows
     * for the admin detail page.
     */
    @Query(
            "select masterKey from LlmProviderMasterKeyEntity masterKey "
                    + "where masterKey.provider = :provider "
                    + "order by masterKey.priority")
    List<LlmProviderMasterKeyEntity> findByProviderIdOrderByPriority(
            @Param("provider") String provider);

    default List<LlmProviderMasterKeyEntity> findByProviderOrderByPriority(LlmProvider provider) {
        return findByProviderIdOrderByPriority(provider.id());
    }

    /**
     * Phase B v2: ordered failover chain — ACTIVE keys only. The router walks this list and tries
     * each key in turn until one succeeds.
     */
    @Query(
            "select masterKey from LlmProviderMasterKeyEntity masterKey "
                    + "where masterKey.provider = :provider "
                    + "and masterKey.status = :status "
                    + "order by masterKey.priority")
    List<LlmProviderMasterKeyEntity> findActiveByProviderIdOrderByPriority(
            @Param("provider") String provider, @Param("status") MasterKeyStatus status);

    default List<LlmProviderMasterKeyEntity> findActiveByProviderOrderByPriority(
            LlmProvider provider, MasterKeyStatus status) {
        return findActiveByProviderIdOrderByPriority(provider.id(), status);
    }

    /**
     * Backwards-compat shim for existing call sites that expect "one canonical key per provider".
     * Returns the lowest-priority ACTIVE key.
     */
    default Optional<LlmProviderMasterKeyEntity> findPrimaryActive(LlmProvider provider) {
        List<LlmProviderMasterKeyEntity> activeKeys =
                findActiveByProviderIdOrderByPriority(provider.id(), MasterKeyStatus.ACTIVE);
        return activeKeys.isEmpty() ? Optional.empty() : Optional.of(activeKeys.get(0));
    }

    /**
     * @deprecated Phase B v1 callers used to {@code findById(provider)} directly. The PK is now
     *     {@code (provider, key_id)}; use {@link #findPrimaryActive} for the "give me the canonical
     *     key for this provider" semantics.
     */
    @Deprecated(forRemoval = false)
    default Optional<LlmProviderMasterKeyEntity> findFirstByProvider(LlmProvider provider) {
        List<LlmProviderMasterKeyEntity> all = findByProviderOrderByPriority(provider);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }
}
