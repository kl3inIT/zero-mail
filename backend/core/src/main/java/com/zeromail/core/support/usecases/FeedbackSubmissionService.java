package com.zeromail.core.support.usecases;

import com.zeromail.core.support.domain.FeedbackStatus;
import com.zeromail.core.support.persistence.FeedbackSubmissionEntity;
import com.zeromail.core.support.persistence.FeedbackSubmissionRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackSubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackSubmissionService.class);
    private static final int MAX_LIST_LIMIT = 100;

    private final FeedbackSubmissionRepository feedbackSubmissionRepository;

    public FeedbackSubmissionService(FeedbackSubmissionRepository feedbackSubmissionRepository) {
        this.feedbackSubmissionRepository =
                Objects.requireNonNull(
                        feedbackSubmissionRepository,
                        "feedbackSubmissionRepository must not be null");
    }

    @Transactional
    public UUID submit(SubmitFeedbackCommand command) {
        UUID submissionId = UUID.randomUUID();
        FeedbackSubmissionEntity entity =
                new FeedbackSubmissionEntity(
                        submissionId,
                        command.tenantId(),
                        command.type(),
                        command.subject(),
                        command.message(),
                        command.contactEmail());
        feedbackSubmissionRepository.save(entity);
        logger.info(
                "event=feedback_submitted tenantId={} type={} submissionId={}",
                command.tenantId(),
                command.type(),
                submissionId);
        return submissionId;
    }

    @Transactional(readOnly = true)
    public FeedbackListQuery.Result list(FeedbackListQuery.Filters filters) {
        int limit = Math.min(filters.limit() > 0 ? filters.limit() : 50, MAX_LIST_LIMIT);
        PageRequest pageable = PageRequest.of(0, limit);
        List<FeedbackSubmissionEntity> rows =
                filters.status() != null
                        ? feedbackSubmissionRepository.findByStatusOrderByCreatedAtDesc(
                                filters.status(), pageable)
                        : feedbackSubmissionRepository.findAllByOrderByCreatedAtDesc(pageable);
        long openCount = feedbackSubmissionRepository.countByStatus(FeedbackStatus.OPEN);
        List<FeedbackListQuery.FeedbackRow> feedbackRows = rows.stream().map(this::toRow).toList();
        return new FeedbackListQuery.Result(feedbackRows, openCount);
    }

    @Transactional
    public void resolve(UUID submissionId, String adminNotes) {
        FeedbackSubmissionEntity entity =
                feedbackSubmissionRepository
                        .findById(submissionId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Feedback submission not found: " + submissionId));
        entity.resolve(adminNotes, Instant.now());
        logger.info("event=feedback_resolved submissionId={}", submissionId);
    }

    @Transactional
    public void reopen(UUID submissionId) {
        FeedbackSubmissionEntity entity =
                feedbackSubmissionRepository
                        .findById(submissionId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Feedback submission not found: " + submissionId));
        entity.reopen();
        logger.info("event=feedback_reopened submissionId={}", submissionId);
    }

    private FeedbackListQuery.FeedbackRow toRow(FeedbackSubmissionEntity entity) {
        return new FeedbackListQuery.FeedbackRow(
                entity.getId(),
                entity.getTenantId(),
                entity.getType(),
                entity.getSubject(),
                entity.getMessage(),
                entity.getContactEmail(),
                entity.getStatus(),
                entity.getAdminNotes(),
                entity.getResolvedAt(),
                entity.getCreatedAt());
    }
}
