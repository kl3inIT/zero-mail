package com.zeromail.core.triage.usecases;

import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.triage.persistence.lowlevel.AuditLogReadRepository;
import com.zeromail.core.triage.projection.AuditLogPage;
import com.zeromail.core.triage.projection.AuditLogPageQuery;
import com.zeromail.core.triage.projection.AuditLogRow;
import com.zeromail.core.triage.projection.DigestSourceItem;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogQueryService {

    private final AuditLogReadRepository auditLogReadRepository;

    public AuditLogQueryService(AuditLogReadRepository auditLogReadRepository) {
        this.auditLogReadRepository =
                Objects.requireNonNull(
                        auditLogReadRepository, "auditLogReadRepository must not be null");
    }

    @Transactional(readOnly = true)
    public AuditLogPage page(UUID tenantId, AuditLogPageQuery query) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        AuditLogPageQuery pageQuery = Objects.requireNonNull(query, "query must not be null");
        Optional<KeysetCursor> decodedCursor = KeysetCursor.decode(pageQuery.cursor());
        decodedCursor
                .filter(KeysetCursor::isNullsLast)
                .ifPresent(
                        _ -> {
                            throw new IllegalArgumentException(
                                    "audit cursor cannot use NULLS_LAST");
                        });

        List<AuditLogRow> rows =
                auditLogReadRepository.findPage(tenantId, pageQuery, decodedCursor);
        boolean hasNextPage = rows.size() > pageQuery.limit();
        List<AuditLogRow> pageItems = hasNextPage ? rows.subList(0, pageQuery.limit()) : rows;
        String nextCursor =
                hasNextPage
                        ? KeysetCursor.encode(
                                pageItems.getLast().createdAt(), pageItems.getLast().auditId())
                        : null;
        return new AuditLogPage(pageItems, nextCursor);
    }

    /**
     * Returns the messages a tenant's rules filed into the digest within {@code [since, until)},
     * most-recent first, capped at {@code limit}. Privacy-clean: each item is sanitized audit
     * metadata plus the Gmail message id the weekly digest re-fetches at send time. Never reads
     * body content.
     */
    @Transactional(readOnly = true)
    public List<DigestSourceItem> findDigestSourceItems(
            UUID tenantId, Instant since, Instant until, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (limit < 1) {
            return List.of();
        }
        return auditLogReadRepository.findDigestSourceItems(tenantId, since, until, limit);
    }
}
