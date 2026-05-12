package com.zeromail.core.thread.persistence;

import com.zeromail.core.thread.domain.ThreadReplyBucket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThreadReplyStatusRepository extends JpaRepository<ThreadReplyStatusEntity, UUID> {

    Optional<ThreadReplyStatusEntity> findByGmailThreadId(String gmailThreadId);

    long countByBucketAndResolvedFalse(ThreadReplyBucket bucket);
}
