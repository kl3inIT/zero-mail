package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.CleanupSenderStatus;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CleanupSenderStatusService {

    private static final Logger log = LoggerFactory.getLogger(CleanupSenderStatusService.class);

    private final JdbcTemplate jdbcTemplate;

    public CleanupSenderStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public int setStatus(
            UUID tenantId, Collection<String> senderEmails, CleanupSenderStatus senderStatus) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(senderStatus, "senderStatus must not be null");
        List<String> normalizedSenderEmails = normalizeSenderEmails(senderEmails);
        int updatedCount = 0;
        for (String senderEmail : normalizedSenderEmails) {
            updatedCount +=
                    jdbcTemplate.update(
                            """
                                    INSERT INTO cleanup_sender_status(
                                        tenant_id, sender_email, status, created_at, updated_at)
                                    VALUES (?, ?, ?, NOW(), NOW())
                                    ON CONFLICT (tenant_id, sender_email)
                                    DO UPDATE SET status = EXCLUDED.status, updated_at = NOW()
                                    """,
                            tenantId,
                            senderEmail,
                            senderStatus.id());
        }
        log.info(
                "event=cleanup_sender_status_set tenantId={} status={} senderCount={}",
                tenantId,
                senderStatus.id(),
                normalizedSenderEmails.size());
        return updatedCount;
    }

    public int clearStatus(UUID tenantId, Collection<String> senderEmails) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        List<String> normalizedSenderEmails = normalizeSenderEmails(senderEmails);
        int removedCount = 0;
        for (String senderEmail : normalizedSenderEmails) {
            removedCount +=
                    jdbcTemplate.update(
                            """
                                    DELETE FROM cleanup_sender_status
                                    WHERE tenant_id = ? AND sender_email = ?
                                    """,
                            tenantId,
                            senderEmail);
        }
        log.info(
                "event=cleanup_sender_status_cleared tenantId={} senderCount={}",
                tenantId,
                normalizedSenderEmails.size());
        return removedCount;
    }

    static List<String> normalizeSenderEmails(Collection<String> senderEmails) {
        Objects.requireNonNull(senderEmails, "senderEmails must not be null");
        LinkedHashSet<String> normalizedSenderEmails = new LinkedHashSet<>();
        for (String senderEmail : senderEmails) {
            if (senderEmail == null || senderEmail.isBlank()) {
                throw new IllegalArgumentException("senderEmail must not be blank");
            }
            String normalizedSenderEmail = senderEmail.trim().toLowerCase(Locale.ROOT);
            if (normalizedSenderEmail.length() > 320) {
                throw new IllegalArgumentException("senderEmail is too long");
            }
            normalizedSenderEmails.add(normalizedSenderEmail);
        }
        return List.copyOf(normalizedSenderEmails);
    }
}
