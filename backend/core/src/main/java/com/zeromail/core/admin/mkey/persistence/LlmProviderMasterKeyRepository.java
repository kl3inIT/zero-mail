package com.zeromail.core.admin.mkey.persistence;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LlmProviderMasterKeyRepository
        extends JpaRepository<LlmProviderMasterKeyEntity, LlmProvider> {

    @Query("select masterKey from LlmProviderMasterKeyEntity masterKey order by masterKey.provider")
    List<LlmProviderMasterKeyEntity> findAllOrderedByProvider();
}
