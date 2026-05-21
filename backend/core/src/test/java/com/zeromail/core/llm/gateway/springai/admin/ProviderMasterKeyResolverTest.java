package com.zeromail.core.llm.gateway.springai.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyEntity;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyRepository;
import com.zeromail.core.shared.crypto.PlatformSecretCipher;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class ProviderMasterKeyResolverTest {

    @Test
    void resolves_decrypted_key_with_provider_secret_version_and_caches_until_invalidation() {
        LlmProviderMasterKeyRepository repository = mock(LlmProviderMasterKeyRepository.class);
        PlatformSecretCipher cipher = testCipher();
        byte[] encryptedKey =
                cipher.encrypt(
                        "provider-key-value".getBytes(StandardCharsets.UTF_8),
                        "platform:master_key:OPENAI");
        when(repository.findById(LlmProvider.OPENAI))
                .thenReturn(
                        Optional.of(
                                new LlmProviderMasterKeyEntity(
                                        LlmProvider.OPENAI,
                                        KeyFormat.OPENAI_FORMAT,
                                        encryptedKey,
                                        (short) 7,
                                        4L,
                                        null,
                                        Instant.parse("2026-05-20T00:00:00Z"),
                                        Instant.parse("2026-05-20T00:00:00Z"),
                                        "https://api.openai.com/v1",
                                        "sk-****key")));
        ProviderMasterKeyResolver resolver =
                new ProviderMasterKeyResolver(
                        repository, cipher, fixedClock(), Duration.ofMinutes(15));

        ProviderMasterKeyResolver.ResolvedMasterKey resolvedKey =
                resolver.resolve(LlmProvider.OPENAI);
        resolvedKey.plaintextKey()[0] = 0;

        assertThat(
                        new String(
                                resolver.resolve(LlmProvider.OPENAI).plaintextKey(),
                                StandardCharsets.UTF_8))
                .isEqualTo("provider-key-value");
        assertThat(resolvedKey.providerSecretVersion()).isEqualTo(4L);
        assertThat(resolvedKey.providerCatalogVersion()).isEqualTo(1L);
        resolver.invalidate(LlmProvider.OPENAI);
        resolver.resolve(LlmProvider.OPENAI);
        verify(repository, org.mockito.Mockito.times(2)).findById(LlmProvider.OPENAI);
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC);
    }

    private static PlatformSecretCipher testCipher() {
        byte[] keyBytes = new byte[32];
        for (int byteIndex = 0; byteIndex < keyBytes.length; byteIndex++) {
            keyBytes[byteIndex] = (byte) (byteIndex + 1);
        }
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        return new PlatformSecretCipher(Map.of(7, secretKey), 7);
    }
}
