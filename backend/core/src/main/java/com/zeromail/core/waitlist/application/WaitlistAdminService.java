package com.zeromail.core.waitlist.application;

import com.zeromail.core.waitlist.domain.WaitlistStatus;
import com.zeromail.core.waitlist.exception.WaitlistEntryNotFoundException;
import com.zeromail.core.waitlist.exception.WaitlistEntryStateException;
import com.zeromail.core.waitlist.persistence.WaitlistEmailEntity;
import com.zeromail.core.waitlist.persistence.WaitlistEmailRepository;
import com.zeromail.core.waitlist.projection.WaitlistEntryPage;
import com.zeromail.core.waitlist.projection.WaitlistEntryProjection;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-side waitlist transitions. Caller (controller) supplies {@code adminId} explicitly from
 * {@code AdminContext}; this service has no direct dependency on the admin auth module so the
 * Spring Modulith boundary stays clean.
 *
 * <p>The mail-sending side effect lives in {@code backend/worker} — this service only flips status
 * to {@code APPROVED} and commits. The worker polls for APPROVED rows on a separate cron and
 * dispatches via Resend; no in-process event bridges the two services.
 */
@Service
public class WaitlistAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(WaitlistAdminService.class);

    private final WaitlistEmailRepository waitlistEmailRepository;
    private final Clock clock;

    public WaitlistAdminService(WaitlistEmailRepository waitlistEmailRepository, Clock clock) {
        this.waitlistEmailRepository = waitlistEmailRepository;
        this.clock = clock;
    }

    @Transactional
    public WaitlistEntryProjection approve(UUID waitlistId, UUID adminId) {
        WaitlistEmailEntity entity =
                waitlistEmailRepository
                        .findByIdForUpdate(waitlistId)
                        .orElseThrow(() -> new WaitlistEntryNotFoundException(waitlistId));
        if (entity.getStatus() != WaitlistStatus.PENDING) {
            throw new WaitlistEntryStateException(entity.getStatus().name(), "approve");
        }
        entity.approve(adminId, clock.instant());
        LOG.info("event=waitlist.approved waitlistId={} adminId={}", waitlistId, adminId);
        return WaitlistEntryProjection.from(entity);
    }

    @Transactional
    public WaitlistEntryProjection reject(UUID waitlistId, UUID adminId) {
        WaitlistEmailEntity entity =
                waitlistEmailRepository
                        .findByIdForUpdate(waitlistId)
                        .orElseThrow(() -> new WaitlistEntryNotFoundException(waitlistId));
        if (entity.getStatus() != WaitlistStatus.PENDING) {
            throw new WaitlistEntryStateException(entity.getStatus().name(), "reject");
        }
        entity.reject(adminId, clock.instant());
        LOG.info("event=waitlist.rejected waitlistId={} adminId={}", waitlistId, adminId);
        return WaitlistEntryProjection.from(entity);
    }

    @Transactional(readOnly = true)
    public WaitlistEntryPage list(WaitlistStatus statusFilter, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WaitlistEmailEntity> resultPage;
        if (statusFilter == null) {
            resultPage = waitlistEmailRepository.findAll(pageable);
        } else {
            resultPage = waitlistEmailRepository.findByStatus(statusFilter, pageable);
        }
        return new WaitlistEntryPage(
                resultPage.map(WaitlistEntryProjection::from).getContent(),
                resultPage.getTotalElements(),
                page,
                size);
    }

    @Transactional(readOnly = true)
    public WaitlistEntryProjection get(UUID waitlistId) {
        WaitlistEmailEntity entity =
                waitlistEmailRepository
                        .findById(waitlistId)
                        .orElseThrow(() -> new WaitlistEntryNotFoundException(waitlistId));
        return WaitlistEntryProjection.from(entity);
    }
}
