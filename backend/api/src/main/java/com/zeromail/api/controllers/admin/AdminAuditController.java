package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.audit.AdminAuditPageRequest;
import com.zeromail.api.dto.admin.audit.AdminAuditPageResponse;
import com.zeromail.api.error.AuditExportTooLargeException;
import com.zeromail.core.admin.audit.projection.AdminAuditPage;
import com.zeromail.core.admin.audit.projection.AdminAuditPageQuery;
import com.zeromail.core.admin.audit.usecases.AdminAuditQueryService;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.audit.usecases.AdminReadEventDebouncer;
import com.zeromail.core.admin.audit.usecases.AuditCsvExporter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@Tag(name = "admin-audit")
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private static final int EXPORT_LIMIT = 10_000;
    private static final Duration READ_EVENT_DEBOUNCE_WINDOW = Duration.ofSeconds(60);

    private final AdminAuditQueryService adminAuditQueryService;
    private final AdminAuditWriter adminAuditWriter;
    private final AuditCsvExporter auditCsvExporter;
    private final AdminReadEventDebouncer adminReadEventDebouncer;

    public AdminAuditController(
            AdminAuditQueryService adminAuditQueryService,
            AdminAuditWriter adminAuditWriter,
            AuditCsvExporter auditCsvExporter,
            AdminReadEventDebouncer adminReadEventDebouncer) {
        this.adminAuditQueryService = adminAuditQueryService;
        this.adminAuditWriter = adminAuditWriter;
        this.auditCsvExporter = auditCsvExporter;
        this.adminReadEventDebouncer = adminReadEventDebouncer;
    }

    @GetMapping("/events")
    public AdminAuditPageResponse events(
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetKind,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest httpServletRequest) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        AdminAuditPageRequest pageRequest =
                new AdminAuditPageRequest(
                        actorEmail, action, targetKind, targetId, from, to, cursor, limit);
        writeReadEventIfNeeded(adminUser, pageRequest, httpServletRequest);
        AdminAuditPage auditPage = adminAuditQueryService.page(pageRequest.toQuery());
        return AdminAuditPageResponse.from(auditPage, pageRequest.nextOffset());
    }

    @GetMapping(value = "/events/csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> csv(
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetKind,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to) {
        AdminContext.currentOrThrow();
        AdminAuditPageQuery exportQuery =
                new AdminAuditPageQuery(
                        EXPORT_LIMIT, 0, actorEmail, action, targetKind, targetId, from, to);
        if (adminAuditQueryService.page(exportQuery).hasNextPage()) {
            throw new AuditExportTooLargeException();
        }
        StreamingResponseBody responseBody =
                outputStream -> auditCsvExporter.streamCsv(exportQuery, outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, csvContentDisposition(from, to))
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(responseBody);
    }

    private void writeReadEventIfNeeded(
            AdminUser adminUser,
            AdminAuditPageRequest pageRequest,
            HttpServletRequest httpServletRequest) {
        String debounceKey =
                "audit-events:"
                        + adminUser.id()
                        + "|"
                        + sessionId(httpServletRequest)
                        + "|"
                        + pageRequest.debounceKey();
        if (!adminReadEventDebouncer.shouldEmit(debounceKey, READ_EVENT_DEBOUNCE_WINDOW)) {
            return;
        }
        adminAuditWriter.writeReadEvent(
                adminUser, "ADMIN_AUDIT_EVENTS_READ", "admin_audit_event", null);
    }

    private static String sessionId(HttpServletRequest httpServletRequest) {
        if (httpServletRequest.getSession(false) == null) {
            return "no-session";
        }
        return httpServletRequest.getSession(false).getId();
    }

    private static String csvContentDisposition(Instant from, Instant to) {
        return "attachment; filename=\"audit-"
                + filenamePart(from)
                + "-"
                + filenamePart(to)
                + ".csv\"";
    }

    private static String filenamePart(Instant instant) {
        return instant == null ? "all" : instant.toString().replace(':', '-');
    }
}
