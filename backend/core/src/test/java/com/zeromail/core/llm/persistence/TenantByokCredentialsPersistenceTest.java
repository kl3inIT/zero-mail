package com.zeromail.core.llm.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantByokCredentialsPersistenceTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired TenantByokCredentialsRepository tenantByokCredentialsRepository;

    @Test
    void persists_and_finds_by_tenant_id() {
        UUID tenantId = UUID.randomUUID();
        byte[] encryptedKey = bytes32();
        seedTenant(tenantId);

        TenantByokCredentialsEntity credentials =
                new TenantByokCredentialsEntity(
                        UUID.randomUUID(),
                        tenantId,
                        BYOKProvider.ANTHROPIC,
                        null,
                        "claude-3-haiku-20240307",
                        encryptedKey,
                        (short) 1);

        saveUnderTenant(tenantId, credentials);

        TenantByokCredentialsEntity foundCredentials =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        tenantByokCredentialsRepository
                                                .findByTenantId(tenantId)
                                                .orElseThrow());
        byte[] rawEncryptedKey =
                jdbcTemplate.queryForObject(
                        "select encrypted_key from tenant_byok_credentials where tenant_id = ?",
                        byte[].class,
                        tenantId);

        assertThat(foundCredentials.getProvider()).isEqualTo(BYOKProvider.ANTHROPIC);
        assertThat(foundCredentials.getEndpoint()).isNull();
        assertThat(foundCredentials.getModel()).isEqualTo("claude-3-haiku-20240307");
        assertThat(foundCredentials.getKeyVersion()).isEqualTo((short) 1);
        assertThat(foundCredentials.getEncryptedKey()).containsExactly(encryptedKey);
        assertThat(rawEncryptedKey).containsExactly(encryptedKey);
    }

    @Test
    void rejects_second_byok_for_same_tenant() {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        saveUnderTenant(
                tenantId,
                new TenantByokCredentialsEntity(
                        UUID.randomUUID(),
                        tenantId,
                        BYOKProvider.OPENAI,
                        "https://llm.example.test/v1",
                        "openai/gpt-5.4-nano",
                        bytes32(),
                        (short) 1));

        TenantByokCredentialsEntity duplicateCredentials =
                new TenantByokCredentialsEntity(
                        UUID.randomUUID(),
                        tenantId,
                        BYOKProvider.ANTHROPIC,
                        null,
                        "claude-3-haiku-20240307",
                        bytes32(),
                        (short) 1);

        assertThatThrownBy(() -> saveUnderTenant(tenantId, duplicateCredentials))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void seedTenant(UUID tenantId) {
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "tenant-" + tenantId);
    }

    private void saveUnderTenant(UUID tenantId, TenantByokCredentialsEntity credentials) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> tenantByokCredentialsRepository.saveAndFlush(credentials));
    }

    private static byte[] bytes32() {
        return new byte[] {
            0, 1, 2, 3, 4, 5, 6, 7,
            8, 9, 10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22, 23,
            24, 25, 26, 27, 28, 29, 30, 31
        };
    }
}
