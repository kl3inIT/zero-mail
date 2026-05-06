package com.zeromail.core.billing.persistence.lowlevel;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.billing.persistence.CreditReservationStaleScanFragment;
import com.zeromail.core.billing.persistence.StaleReservation;

@Repository
public class CreditReservationRepositoryImpl implements CreditReservationStaleScanFragment {

    private final JdbcTemplate jdbcTemplate;

    public CreditReservationRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StaleReservation> findStalePendingProjections(Instant olderThan, int limitRows) {
        return jdbcTemplate.query(
                """
                SELECT id, tenant_id, created_at, amount_credits, call_site
                  FROM credit_reservation
                 WHERE status = 'PENDING' AND created_at < ?
                 ORDER BY created_at
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """,
                (resultSet, rowIndex) -> new StaleReservation(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getInt("amount_credits"),
                        CallSite.fromId(resultSet.getString("call_site"))),
                Timestamp.from(olderThan),
                limitRows);
    }
}
