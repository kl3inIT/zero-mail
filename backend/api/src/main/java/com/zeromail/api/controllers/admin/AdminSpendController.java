package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.spend.SpendDashboardResponse;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.audit.usecases.AdminReadEventDebouncer;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.config.AdminProperties;
import com.zeromail.core.admin.spend.exception.SpendExportTooLargeException;
import com.zeromail.core.admin.spend.exception.SpendInvalidRangeException;
import com.zeromail.core.admin.spend.projection.SpendDashboardSnapshot;
import com.zeromail.core.admin.spend.projection.SpendQuery;
import com.zeromail.core.admin.spend.usecases.SpendAggregateQueryService;
import com.zeromail.core.admin.spend.usecases.SpendCsvExporter;
import com.zeromail.core.llm.domain.LlmProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Admin spend-dashboard endpoints under {@code /api/admin/spend}. Aggregate-only metadata; never
 * per-prompt drill-down (OPS-SPEND-01/02 + ARCH-11).
 *
 * <p>Read auditing (R-8F-H4): each {@code GET /dashboard} writes one {@code admin_read_event} row
 * with {@code target_kind=SPEND_DASHBOARD}, debounced by {@code (adminId, sessionId, rangeHash)}
 * for 60s. Auto-refresh ticks of the same range in the same session do not flood the audit table.
 *
 * <p>CSV export (T-08-56): allowed columns are enumerated by {@link SpendCsvExporter}; never
 * carries tenant_id, prompt text, completion text, or any content-shaped field.
 */
@RestController
@Tag(name = "admin-spend")
@RequestMapping("/api/admin/spend")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSpendController {

    private static final Duration READ_EVENT_DEBOUNCE_WINDOW = Duration.ofSeconds(60);
    private static final String READ_ACTION = "ADMIN_SPEND_DASHBOARD_READ";
    private static final String READ_TARGET_KIND = "SPEND_DASHBOARD";

    private final SpendAggregateQueryService spendAggregateQueryService;
    private final SpendCsvExporter spendCsvExporter;
    private final AdminAuditWriter adminAuditWriter;
    private final AdminReadEventDebouncer adminReadEventDebouncer;
    private final LocalDate rowLevelClassificationSince;
    private final Clock clock;

    public AdminSpendController(
            SpendAggregateQueryService spendAggregateQueryService,
            SpendCsvExporter spendCsvExporter,
            AdminAuditWriter adminAuditWriter,
            AdminReadEventDebouncer adminReadEventDebouncer,
            AdminProperties adminProperties,
            Clock clock) {
        this.spendAggregateQueryService =
                Objects.requireNonNull(
                        spendAggregateQueryService, "spendAggregateQueryService must not be null");
        this.spendCsvExporter =
                Objects.requireNonNull(spendCsvExporter, "spendCsvExporter must not be null");
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
        this.adminReadEventDebouncer =
                Objects.requireNonNull(
                        adminReadEventDebouncer, "adminReadEventDebouncer must not be null");
        Objects.requireNonNull(adminProperties, "adminProperties must not be null");
        this.rowLevelClassificationSince = adminProperties.spend().rowLevelClassificationSince();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @GetMapping("/dashboard")
    public SpendDashboardResponse dashboard(
            @RequestParam(name = "from") Instant from,
            @RequestParam(name = "to") Instant to,
            @RequestParam(name = "providers", required = false) Set<LlmProvider> providers,
            @RequestParam(name = "features", required = false) Set<Feature> features,
            HttpServletRequest httpServletRequest) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        SpendQuery spendQuery = buildQuery(from, to, providers, features);
        writeReadEventIfNeeded(adminUser, spendQuery, httpServletRequest);
        SpendDashboardSnapshot snapshot = spendAggregateQueryService.snapshot(spendQuery);
        return SpendDashboardResponse.from(snapshot, rowLevelClassificationSince);
    }

    @GetMapping(value = "/dashboard/csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @RequestParam(name = "from") Instant from,
            @RequestParam(name = "to") Instant to,
            @RequestParam(name = "providers", required = false) Set<LlmProvider> providers,
            @RequestParam(name = "features", required = false) Set<Feature> features) {
        AdminContext.currentOrThrow();
        SpendQuery spendQuery = buildQuery(from, to, providers, features);
        // R-8F-H5 pre-query: rejects > 10k row estimates BEFORE streaming starts.
        int estimate = spendCsvExporter.estimateRowCount(spendQuery);
        if (estimate > SpendCsvExporter.MAX_ROWS) {
            throw new SpendExportTooLargeException(estimate, SpendCsvExporter.MAX_ROWS);
        }
        StreamingResponseBody responseBody =
                outputStream -> {
                    try {
                        spendCsvExporter.streamCsv(spendQuery, outputStream);
                    } catch (IOException ioException) {
                        throw new UncheckedIOException(ioException);
                    }
                };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, csvContentDisposition(from, to))
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(responseBody);
    }

    private SpendQuery buildQuery(
            Instant from, Instant to, Set<LlmProvider> providers, Set<Feature> features) {
        validateRange(from, to);
        return new SpendQuery(
                from,
                to,
                providers == null || providers.isEmpty()
                        ? Optional.empty()
                        : Optional.of(providers),
                features == null || features.isEmpty() ? Optional.empty() : Optional.of(features));
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new SpendInvalidRangeException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new SpendInvalidRangeException("from must not be after to");
        }
        Instant maxFutureTo = clock.instant().plus(Duration.ofDays(1));
        if (to.isAfter(maxFutureTo)) {
            throw new SpendInvalidRangeException("to must not be more than one day in the future");
        }
    }

    private void writeReadEventIfNeeded(
            AdminUser adminUser, SpendQuery spendQuery, HttpServletRequest httpServletRequest) {
        String debounceKey =
                "spend-dashboard:"
                        + adminUser.id()
                        + "|"
                        + sessionId(httpServletRequest)
                        + "|"
                        + rangeHash(spendQuery);
        if (!adminReadEventDebouncer.shouldEmit(debounceKey, READ_EVENT_DEBOUNCE_WINDOW)) {
            return;
        }
        adminAuditWriter.writeReadEvent(adminUser, READ_ACTION, READ_TARGET_KIND, null);
    }

    private static String sessionId(HttpServletRequest httpServletRequest) {
        if (httpServletRequest.getSession(false) == null) {
            return "no-session";
        }
        return httpServletRequest.getSession(false).getId();
    }

    private static String rangeHash(SpendQuery spendQuery) {
        return spendQuery.from().getEpochSecond()
                + ":"
                + spendQuery.to().getEpochSecond()
                + ":"
                + spendQuery
                        .providers()
                        .map(
                                set ->
                                        set.stream()
                                                .map(LlmProvider::id)
                                                .sorted()
                                                .collect(Collectors.joining(",")))
                        .orElse("")
                + ":"
                + spendQuery
                        .features()
                        .map(
                                set ->
                                        set.stream()
                                                .map(Enum::name)
                                                .sorted()
                                                .collect(Collectors.joining(",")))
                        .orElse("");
    }

    private static String csvContentDisposition(Instant from, Instant to) {
        return "attachment; filename=\"spend-"
                + filenamePart(from)
                + "-"
                + filenamePart(to)
                + ".csv\"";
    }

    private static String filenamePart(Instant instant) {
        return instant == null ? "all" : instant.toString().replace(':', '-');
    }
}
