package com.zeromail.core.billing.persistence.lowlevel;

import com.zeromail.core.billing.domain.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentTenantLookup;
import com.zeromail.core.billing.persistence.BillingTopupIntentTenantLookupFragment;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BillingTopupIntentRepositoryImpl implements BillingTopupIntentTenantLookupFragment {

    private final JdbcTemplate jdbcTemplate;

    public BillingTopupIntentRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<BillingTopupIntentTenantLookup> findTenantLookupByCode(String code) {
        return jdbcTemplate
                .query(
                        """
                SELECT id,
                       tenant_id,
                       code,
                       amount_vnd,
                       package_code_snapshot,
                       credit_amount_snapshot,
                       status,
                       expires_at
                  FROM billing_topup_intent
                 WHERE code = ?
                 LIMIT 1
                """,
                        (resultSet, rowIndex) ->
                                new BillingTopupIntentTenantLookup(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getObject("tenant_id", UUID.class),
                                        resultSet.getString("code"),
                                        resultSet.getLong("amount_vnd"),
                                        resultSet.getString("package_code_snapshot"),
                                        resultSet.getObject(
                                                "credit_amount_snapshot", Integer.class),
                                        BillingTopupIntentStatus.fromId(
                                                resultSet.getString("status")),
                                        resultSet.getObject("expires_at", OffsetDateTime.class)),
                        code)
                .stream()
                .findFirst();
    }
}
