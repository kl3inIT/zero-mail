package com.zeromail.core.admin.tenant.persistence.lowlevel;

import com.zeromail.core.admin.tenant.usecases.TenantActivityRequestContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantActivityEventWriteRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public TenantActivityEventWriteRepository(
            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
    }

    public void insert(
            UUID tenantId,
            String eventType,
            String eventStatus,
            String detail,
            TenantActivityRequestContext requestContext,
            Instant occurredAt,
            Integer durationSeconds,
            String source) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(eventStatus, "eventStatus must not be null");
        Objects.requireNonNull(requestContext, "requestContext must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(source, "source must not be null");

        namedParameterJdbcTemplate.update(
                """
                        INSERT INTO tenant_activity_event (
                            tenant_id, event_type, event_status, detail,
                            occurred_at, duration_seconds, source
                        )
                        VALUES (
                            :tenantId, :eventType, :eventStatus, :detail,
                            :occurredAt, :durationSeconds, :source
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("eventType", eventType)
                        .addValue("eventStatus", eventStatus)
                        .addValue("detail", detail)
                        .addValue("occurredAt", Timestamp.from(occurredAt))
                        .addValue("durationSeconds", durationSeconds)
                        .addValue("source", source));
    }
}
