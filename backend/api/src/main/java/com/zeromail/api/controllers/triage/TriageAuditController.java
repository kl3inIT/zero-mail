package com.zeromail.api.controllers.triage;

import com.zeromail.api.dto.triage.AuditListResponse;
import com.zeromail.api.dto.triage.UndoAuditResponse;
import com.zeromail.api.error.InvalidCursorException;
import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.projection.AuditLogPage;
import com.zeromail.core.triage.projection.AuditLogPageQuery;
import com.zeromail.core.triage.usecases.AuditLogQueryService;
import com.zeromail.core.triage.usecases.TriageUndoService;
import com.zeromail.core.triage.usecases.UndoAuditCommand;
import com.zeromail.core.triage.usecases.UndoAuditResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "triage")
public class TriageAuditController {

    private static final Logger log = LoggerFactory.getLogger(TriageAuditController.class);

    private final TriageUndoService triageUndoService;
    private final AuditLogQueryService auditLogQueryService;

    public TriageAuditController(
            TriageUndoService triageUndoService, AuditLogQueryService auditLogQueryService) {
        this.triageUndoService = triageUndoService;
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping("/api/triage/audit")
    public AuditListResponse list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant since,
            @RequestParam(required = false) Instant until) {
        UUID tenantId = TenantContext.currentTenantUuid();
        validateAuditCursor(cursor);
        AuditLogPageQuery query = new AuditLogPageQuery(limit, cursor, action, since, until);
        AuditLogPage page = auditLogQueryService.page(tenantId, query);
        log.info("event=triage_audit_listed tenantId={} limit={}", tenantId, query.limit());
        return AuditListResponse.from(page);
    }

    @PostMapping("/api/triage/audit/{auditId}/undo")
    public UndoAuditResponse undo(@PathVariable UUID auditId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        UndoAuditResult undoAuditResult =
                triageUndoService.undo(new UndoAuditCommand(auditId, tenantId));
        log.info("event=triage_undo_requested tenantId={} auditId={}", tenantId, auditId);
        return UndoAuditResponse.from(undoAuditResult);
    }

    private static void validateAuditCursor(String cursor) {
        try {
            KeysetCursor.decode(cursor)
                    .ifPresent(
                            keysetCursor -> {
                                if (keysetCursor.isNullsLast()) {
                                    throw new IllegalArgumentException(
                                            "audit cursor cannot use nulls-last");
                                }
                                UUID.fromString(keysetCursor.id());
                            });
        } catch (IllegalArgumentException invalidCursor) {
            throw new InvalidCursorException(invalidCursor);
        }
    }
}
