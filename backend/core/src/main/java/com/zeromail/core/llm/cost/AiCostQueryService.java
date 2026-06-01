package com.zeromail.core.llm.cost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCostQueryService {

    private static final Duration COST_WINDOW = Duration.ofDays(7);
    private static final BigDecimal ZERO_USD = BigDecimal.ZERO.setScale(2);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final Clock clock;

    public AiCostQueryService(NamedParameterJdbcTemplate namedParameterJdbcTemplate, Clock clock) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(namedParameterJdbcTemplate, "namedParameterJdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true, timeout = 5)
    public BigDecimal totalUsdLast7Days(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Instant cutoff = clock.instant().minus(COST_WINDOW);
        BigDecimal total =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT COALESCE(SUM(total_cost_usd), 0) AS total_usd
                        FROM llm_call_audit
                        WHERE tenant_id = :tenant_id
                          AND created_at >= :cutoff
                        """,
                        new MapSqlParameterSource()
                                .addValue("tenant_id", tenantId)
                                .addValue("cutoff", Timestamp.from(cutoff)),
                        BigDecimal.class);
        if (total == null) {
            return ZERO_USD;
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
