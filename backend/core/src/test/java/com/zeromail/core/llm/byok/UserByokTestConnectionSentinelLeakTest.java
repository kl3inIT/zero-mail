package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.gateway.springai.ConnectionTestResult;
import com.zeromail.core.llm.gateway.springai.ProviderConnectionTester;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserByokTestConnectionSentinelLeakTest {

    @Test
    void providerErrorModelPayloadIsDroppedWhenConnectionResultIsNotOk() throws Exception {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000009433");
        RefreshTokenCipher refreshTokenCipher = refreshTokenCipher();
        UserByokKeyEntity userByokKey = userByokKey(tenantId, refreshTokenCipher);
        UserByokKeyRepository userByokKeyRepository = mock(UserByokKeyRepository.class);
        when(userByokKeyRepository.findByTenantId(tenantId)).thenReturn(Optional.of(userByokKey));
        when(userByokKeyRepository.save(any(UserByokKeyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BaseUrlValidator baseUrlValidator = mock(BaseUrlValidator.class);
        when(baseUrlValidator.validate("https://api.openai.com/v1"))
                .thenReturn(
                        new BaseUrlValidator.ValidatedTarget(
                                URI.create("https://api.openai.com/v1"),
                                InetAddress.getByName("8.8.8.8")));
        ProviderConnectionTester providerConnectionTester = mock(ProviderConnectionTester.class);
        when(providerConnectionTester.probeConnection(
                        any(), any(BaseUrlValidator.ValidatedTarget.class), any(byte[].class)))
                .thenReturn(
                        new ConnectionTestResult(
                                MasterKeyTestResult.INVALID_KEY, List.of("SENTINEL_LEAK_X9Z2")));
        UserByokService userByokService =
                new UserByokService(
                        userByokKeyRepository,
                        new ProviderAllowList(),
                        baseUrlValidator,
                        refreshTokenCipher,
                        providerConnectionTester,
                        noOpRateLimiter(),
                        new ObjectMapper(),
                        Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC));

        ConnectionTestResult result = userByokService.testConnection(tenantId);

        assertThat(result.result()).isEqualTo(MasterKeyTestResult.INVALID_KEY);
        assertThat(result.models()).isEmpty();
        assertThat(userByokKey.getLastTestModelsJson()).isNull();
    }

    private static UserByokKeyEntity userByokKey(
            UUID tenantId, RefreshTokenCipher refreshTokenCipher) {
        byte[] encryptedKey =
                refreshTokenCipher.encrypt(
                        "sk-sentinel-test-key".getBytes(StandardCharsets.UTF_8),
                        tenantId.toString());
        return new UserByokKeyEntity(
                tenantId,
                UserByokKeyEntity.Provider.OPENAI,
                "https://api.openai.com/v1",
                encryptedKey,
                null);
    }

    private static ByokRateLimiter noOpRateLimiter() {
        ByokRateLimiter byokRateLimiter = mock(ByokRateLimiter.class);
        return byokRateLimiter;
    }

    private static RefreshTokenCipher refreshTokenCipher() {
        SecretKey secretKey = new SecretKeySpec(new byte[32], "AES");
        return new RefreshTokenCipher(Map.of(1, secretKey), 1);
    }
}
