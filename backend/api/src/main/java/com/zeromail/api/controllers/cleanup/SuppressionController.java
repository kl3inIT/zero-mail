package com.zeromail.api.controllers.cleanup;

import com.zeromail.api.dto.cleanup.SuppressionAddRequest;
import com.zeromail.api.dto.cleanup.SuppressionEntryResponse;
import com.zeromail.api.dto.cleanup.SuppressionListResponse;
import com.zeromail.api.error.ErrorTypes;
import com.zeromail.core.cleanup.domain.SuppressionReason;
import com.zeromail.core.cleanup.projection.SenderSuppressionProjection;
import com.zeromail.core.cleanup.usecases.SuppressionCrudService;
import com.zeromail.core.cleanup.usecases.SuppressionCrudService.AddSuppressionCommand;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * UNS-02 — CRUD endpoints for the per-tenant sender suppression list. Thin controller per
 * CONVENTIONS §1.
 *
 * <p>The wire-level {@link SuppressionAddRequest} carries a single {@code senderEmailOrDomain}
 * string — the controller disambiguates email vs domain by presence of the {@code '@'} character
 * before constructing the underlying {@link AddSuppressionCommand}. Manual additions always carry
 * {@link SuppressionReason#MANUAL}; the {@code REPLIED} / {@code AUTO} reasons are reserved for
 * {@code SuppressionAutoAddService} (Wave 3) and never reach this controller.
 *
 * <p>{@link SuppressionCrudService#remove} throws {@link java.util.NoSuchElementException} on
 * missing rows; the global handler maps that to a generic 500 today, so a controller-local
 * {@code @ExceptionHandler} maps it to the SPEC's expected 404 instead.
 *
 * <p>Privacy invariant (UNS-09): the log line records the target shape ({@code "email"} vs {@code
 * "domain"}) and counts only — never the raw email value.
 */
@RestController
@Tag(name = "cleanup")
@RequestMapping("/api/cleanup/suppression")
public class SuppressionController {

    private static final Logger log = LoggerFactory.getLogger(SuppressionController.class);

    private final SuppressionCrudService suppressionCrudService;

    public SuppressionController(SuppressionCrudService suppressionCrudService) {
        this.suppressionCrudService = suppressionCrudService;
    }

    @GetMapping
    public SuppressionListResponse list() {
        UUID tenantId = TenantContext.currentTenantUuid();
        List<SenderSuppressionProjection> projections = suppressionCrudService.list(tenantId);
        log.info(
                "event=cleanup_suppression_list_requested tenantId={} count={}",
                tenantId,
                projections.size());
        return SuppressionListResponse.from(projections);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuppressionEntryResponse add(@Valid @RequestBody SuppressionAddRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        AddSuppressionCommand command = buildCommand(request.senderEmailOrDomain());
        SenderSuppressionProjection added = suppressionCrudService.addManual(tenantId, command);
        log.info(
                "event=cleanup_suppression_add_requested tenantId={} target={}",
                tenantId,
                command.isSenderEmail() ? "email" : "domain");
        return SuppressionEntryResponse.from(added);
    }

    @DeleteMapping("/{suppressionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID suppressionId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        suppressionCrudService.remove(tenantId, suppressionId);
        log.info(
                "event=cleanup_suppression_remove_requested tenantId={} suppressionId={}",
                tenantId,
                suppressionId);
    }

    /**
     * Disambiguate the single user-friendly input into the XOR-validated {@link
     * AddSuppressionCommand}: presence of {@code '@'} marks an email-shaped target; otherwise treat
     * the value as a domain. The {@code AddSuppressionCommand} compact constructor enforces XOR +
     * reason non-null — any malformed input raises {@link IllegalArgumentException}, which the
     * global handler maps to HTTP 400.
     */
    private static AddSuppressionCommand buildCommand(String senderEmailOrDomain) {
        String normalized = senderEmailOrDomain.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("@")) {
            return new AddSuppressionCommand(normalized, null, SuppressionReason.MANUAL);
        }
        return new AddSuppressionCommand(null, normalized, SuppressionReason.MANUAL);
    }

    /**
     * Map the service-layer {@link NoSuchElementException} (thrown by {@link
     * SuppressionCrudService#remove} on a missing row) to HTTP 404. Without this controller-local
     * handler, the exception falls through to the JVM default and the global handler returns a
     * misleading 500. Tenant-scoping in the service guarantees that a row owned by another tenant
     * appears identical to "not found" — no information leak.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException exception) {
        log.warn(
                "event=cleanup_suppression_not_found tenantId={} reason={}",
                tenantIdForLog(),
                exception.getClass().getSimpleName());
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setType(ErrorTypes.NOT_FOUND);
        problemDetail.setTitle("Suppression entry not found");
        problemDetail.setDetail("The requested suppression entry was not found for this tenant.");
        problemDetail.setProperty("code", "error.cleanup.suppression.not_found");
        problemDetail.setProperty("params", Map.of());
        problemDetail.setProperty("message", "error.cleanup.suppression.not_found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    private static String tenantIdForLog() {
        try {
            return TenantContext.currentOrThrow();
        } catch (RuntimeException tenantContextMissing) {
            return "unknown";
        }
    }
}
