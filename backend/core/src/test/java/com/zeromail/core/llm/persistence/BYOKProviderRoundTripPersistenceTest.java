package com.zeromail.core.llm.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;

class BYOKProviderRoundTripPersistenceTest extends PostgresContainerTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TenantByokCredentialsRepository tenantByokCredentialsRepository;

    @ParameterizedTest
    @EnumSource(BYOKProvider.class)
    void persists_lowercase_id_and_reads_back_enum(BYOKProvider provider) {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        TenantByokCredentialsEntity credentials = new TenantByokCredentialsEntity(
                UUID.randomUUID(),
                tenantId,
                provider,
                providerEndpoint(provider),
                providerModel(provider),
                bytes32(),
                (short) 1);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> tenantByokCredentialsRepository.saveAndFlush(credentials));

        String rawProvider = jdbcTemplate.queryForObject(
                "select provider from tenant_byok_credentials where tenant_id = ?",
                String.class,
                tenantId);
        BYOKProvider foundProvider = ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> tenantByokCredentialsRepository.findByTenantId(tenantId).orElseThrow().getProvider());

        assertThat(rawProvider).isEqualTo(provider.id());
        assertThat(foundProvider).isEqualTo(provider);
    }

    @Test
    void enum_constant_name_would_violate_check_constraint_proof() {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into tenant_byok_credentials(id, tenant_id, provider, endpoint, encrypted_key, key_version) "
                        + "values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                "ANTHROPIC",
                null,
                bytes32(),
                (short) 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void seedTenant(UUID tenantId) {
        jdbcTemplate.update("insert into tenants(id, display_name) values (?, ?)", tenantId, "tenant-" + tenantId);
    }

    private static String providerEndpoint(BYOKProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> "https://api.anthropic.com/v1";
            case DEEPSEEK -> "https://api.deepseek.com";
            case GOOGLE_GENAI -> "https://generativelanguage.googleapis.com/v1beta";
            case OPENAI -> "https://llm.example.test/v1";
        };
    }

    private static String providerModel(BYOKProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> "claude-3-haiku-20240307";
            case DEEPSEEK -> "deepseek-chat";
            case GOOGLE_GENAI -> "gemini-2.0-flash";
            case OPENAI -> "openai/gpt-4o-mini";
        };
    }

    private static byte[] bytes32() {
        return new byte[] {
                31, 30, 29, 28, 27, 26, 25, 24,
                23, 22, 21, 20, 19, 18, 17, 16,
                15, 14, 13, 12, 11, 10, 9, 8,
                7, 6, 5, 4, 3, 2, 1, 0
        };
    }
}
