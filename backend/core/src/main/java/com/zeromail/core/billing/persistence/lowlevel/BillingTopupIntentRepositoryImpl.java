package com.zeromail.core.billing.persistence.lowlevel;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentTenantLookup;
import com.zeromail.core.billing.persistence.BillingTopupIntentTenantLookupFragment;

@Repository
public class BillingTopupIntentRepositoryImpl implements BillingTopupIntentTenantLookupFragment {

    private final JdbcTemplate jdbcTemplate;

    public BillingTopupIntentRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<BillingTopupIntentTenantLookup> findTenantLookupByCode(String code) {
        return jdbcTemplate.query(
                """
                SELECT id, tenant_id, code, amount_vnd, status, expires_at
                  FROM billing_topup_intent
                 WHERE code = ?
                 LIMIT 1
                """,
                (resultSet, rowIndex) -> new BillingTopupIntentTenantLookup(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getString("code"),
                        resultSet.getLong("amount_vnd"),
                        BillingTopupIntentStatus.fromId(resultSet.getString("status")),
                        resultSet.getObject("expires_at", OffsetDateTime.class)),
                code).stream().findFirst();
    }
}
