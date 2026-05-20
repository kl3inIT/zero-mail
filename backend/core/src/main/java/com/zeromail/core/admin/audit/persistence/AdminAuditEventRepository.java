package com.zeromail.core.admin.audit.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEventEntity, UUID> {

    @Query(
            value =
                    """
                    SELECT hmac_chain_hash
                    FROM admin_audit_event
                    ORDER BY chain_index DESC
                    LIMIT 1
                    FOR UPDATE
                    """,
            nativeQuery = true)
    Optional<byte[]> findLatestHmacForUpdate();
}
