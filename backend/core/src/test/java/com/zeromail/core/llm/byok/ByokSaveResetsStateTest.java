package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.support.PostgresContainerTest;
import jakarta.persistence.EntityManager;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

class ByokSaveResetsStateTest extends PostgresContainerTest {

    @Autowired UserByokService userByokService;

    @Autowired UserByokKeyRepository userByokKeyRepository;

    @Autowired RefreshTokenCipher refreshTokenCipher;

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired EntityManager entityManager;

    @MockitoBean HostResolver hostResolver;

    @Test
    @Transactional
    void savingByokCredentialResetsActivationAndLastTestState() throws Exception {
        when(hostResolver.resolve(anyString()))
                .thenReturn(new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000009421");
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?) on conflict (id) do nothing",
                tenantId,
                "byok-save-reset-test");
        byte[] encryptedKey =
                refreshTokenCipher.encrypt(
                        "sk-original-key".getBytes(StandardCharsets.UTF_8), tenantId.toString());
        UserByokKeyEntity existingKey =
                new UserByokKeyEntity(
                        tenantId,
                        UserByokKeyEntity.Provider.OPENAI,
                        "https://api.openai.com/v1",
                        encryptedKey,
                        "gpt-4o");
        existingKey.recordConnectionTest(
                UserByokKeyEntity.LastTestResult.OK,
                Instant.parse("2026-05-20T00:00:00Z"),
                "[\"gpt-4o\"]");
        existingKey.activate();
        entityManager.persist(existingKey);
        entityManager.flush();
        entityManager.clear();

        userByokService.save(
                tenantId,
                new ByokSaveCommand(
                        "ANTHROPIC", "https://api.anthropic.com/v1", "sk-new-key-value"));
        entityManager.flush();
        entityManager.clear();

        UserByokKeyEntity reloadedKey =
                userByokKeyRepository.findByTenantId(tenantId).orElseThrow();
        assertThat(reloadedKey.getProviderId()).isEqualTo("ANTHROPIC");
        assertThat(reloadedKey.getBaseUrl()).isEqualTo("https://api.anthropic.com/v1");
        assertThat(reloadedKey.isActive()).isFalse();
        assertThat(reloadedKey.getLastTestResultId()).isNull();
        assertThat(reloadedKey.getLastTestedAt()).isNull();
        assertThat(reloadedKey.getModelId()).isNull();
        assertThat(reloadedKey.getLastTestModelsJson()).isNull();
    }
}
