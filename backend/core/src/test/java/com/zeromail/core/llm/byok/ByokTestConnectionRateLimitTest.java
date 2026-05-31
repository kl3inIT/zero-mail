package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.domain.MasterKeyTestResult;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class ByokTestConnectionRateLimitTest {

    @Test
    void eleventhByokTestConnectionWithinOneHourIsRejected() throws Exception {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000009423");
        RefreshTokenCipher refreshTokenCipher = refreshTokenCipher();
        byte[] encryptedKey =
                refreshTokenCipher.encrypt(
                        "sk-rate-limit-key".getBytes(StandardCharsets.UTF_8), tenantId.toString());
        UserByokKeyEntity userByokKey =
                new UserByokKeyEntity(
                        tenantId,
                        UserByokKeyEntity.Provider.OPENAI,
                        "https://api.openai.com/v1",
                        encryptedKey,
                        null);
        UserByokKeyRepository userByokKeyRepository = mock(UserByokKeyRepository.class);
        when(userByokKeyRepository.findByTenantId(tenantId)).thenReturn(Optional.of(userByokKey));
        when(userByokKeyRepository.save(any(UserByokKeyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BaseUrlValidator baseUrlValidator = mock(BaseUrlValidator.class);
        when(baseUrlValidator.validate("https://api.openai.com/v1"))
                .thenReturn(
                        new BaseUrlValidator.ValidatedTarget(
                                URI.create("https://api.openai.com/v1"),
                                InetAddress.getByName("93.184.216.34")));
        ProviderConnectionTester providerConnectionTester = mock(ProviderConnectionTester.class);
        when(providerConnectionTester.probeConnection(
                        any(), any(BaseUrlValidator.ValidatedTarget.class), any(byte[].class)))
                .thenReturn(
                        new ConnectionTestResult(MasterKeyTestResult.OK, List.of("gpt-4o-mini")));
        UserByokService userByokService =
                new UserByokService(
                        userByokKeyRepository,
                        new ProviderAllowList(),
                        baseUrlValidator,
                        refreshTokenCipher,
                        providerConnectionTester,
                        rateLimiterReturningCountsOneThroughEleven(),
                        new ObjectMapper(),
                        Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(userByokService.testConnection(tenantId).result())
                    .isEqualTo(MasterKeyTestResult.OK);
        }
        assertThatThrownBy(() -> userByokService.testConnection(tenantId))
                .isInstanceOf(ByokRateLimiter.RateLimitExceededException.class)
                .satisfies(
                        thrown ->
                                assertThat(
                                                ((ByokRateLimiter.RateLimitExceededException)
                                                                thrown)
                                                        .errorCode())
                                        .isEqualTo("ai.byok.test_connection.rate_limited"));
    }

    private static ByokRateLimiter rateLimiterReturningCountsOneThroughEleven() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any()))
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
        return new ByokRateLimiter(
                () -> redisTemplate,
                Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC));
    }

    private static RefreshTokenCipher refreshTokenCipher() {
        SecretKey secretKey = new SecretKeySpec(new byte[32], "AES");
        return new RefreshTokenCipher(Map.of(1, secretKey), 1);
    }
}
