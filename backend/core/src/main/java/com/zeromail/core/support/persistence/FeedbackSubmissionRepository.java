package com.zeromail.core.support.persistence;

import com.zeromail.core.support.domain.FeedbackStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackSubmissionRepository
        extends JpaRepository<FeedbackSubmissionEntity, UUID> {

    List<FeedbackSubmissionEntity> findByStatusOrderByCreatedAtDesc(
            FeedbackStatus status, Pageable pageable);

    List<FeedbackSubmissionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(FeedbackStatus status);
}
