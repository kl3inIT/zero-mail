package com.zeromail.core.admin.audit.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AdminReadEventRepository extends JpaRepository<AdminReadEventEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            value = "DELETE FROM admin_read_event WHERE created_at < :retentionCutoff",
            nativeQuery = true)
    int deleteOlderThan(@Param("retentionCutoff") Instant retentionCutoff);
}
