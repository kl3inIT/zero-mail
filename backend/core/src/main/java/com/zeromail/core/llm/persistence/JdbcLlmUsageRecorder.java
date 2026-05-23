package com.zeromail.core.llm.persistence;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.usecases.LlmUsageRecord;
import com.zeromail.core.llm.usecases.LlmUsageRecorder;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcLlmUsageRecorder implements LlmUsageRecorder {

    private static final String INSERT_USAGE_SQL =
            """
            INSERT INTO llm_call_audit(
                id,
                tenant_id,
                provider,
                feature,
                model_id,
                credential_source,
                prompt_tokens,
                completion_tokens,
                total_cost_usd,
                call_site,
                charged_credits
            )
            VALUES (
                :id,
                :tenantId,
                :provider,
                :feature,
                :modelId,
                :credentialSource,
                :promptTokens,
                :completionTokens,
                :totalCostUsd,
                :callSite,
                :chargedCredits
            )
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public JdbcLlmUsageRecorder(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public void record(LlmUsageRecord usageRecord) {
        namedParameterJdbcTemplate.update(INSERT_USAGE_SQL, parameters(usageRecord));
    }

    private MapSqlParameterSource parameters(LlmUsageRecord usageRecord) {
        return new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("tenantId", usageRecord.tenantId())
                .addValue("provider", usageRecord.provider())
                .addValue("feature", featureIdFor(usageRecord.callSite()))
                .addValue("modelId", usageRecord.modelId())
                .addValue("credentialSource", usageRecord.credentialSource())
                .addValue("promptTokens", Math.max(0, usageRecord.usage().promptTokens()))
                .addValue("completionTokens", Math.max(0, usageRecord.usage().completionTokens()))
                .addValue("totalCostUsd", BigDecimal.ZERO)
                .addValue("callSite", usageRecord.callSite().id())
                .addValue("chargedCredits", usageRecord.chargedCredits());
    }

    private String featureIdFor(CallSite callSite) {
        return switch (callSite) {
            case DRAFT -> "DRAFT";
            case TRIAGE, TRIAGE_PLATFORM_LLM, TRIAGE_DETERMINISTIC -> "TRIAGE";
            case PREVIEW -> "CHAT";
        };
    }
}
