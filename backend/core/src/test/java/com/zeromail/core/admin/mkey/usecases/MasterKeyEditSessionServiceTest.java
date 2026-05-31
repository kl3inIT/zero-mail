package com.zeromail.core.admin.mkey.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.llm.domain.LlmProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MasterKeyEditSessionServiceTest {

    @Test
    void mint_stores_five_minute_token_and_returns_expiry() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        MasterKeyEditSessionService editSessionService =
                new MasterKeyEditSessionService(
                        () -> redisTemplate, () -> "fixed-token", fixedClock());
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000111");

        MasterKeyEditSessionService.EditSession editSession =
                editSessionService.mint(actorId, LlmProvider.OPENAI);

        assertThat(editSession.token()).isEqualTo("fixed-token");
        assertThat(editSession.expiresAt()).isEqualTo(Instant.parse("2026-05-20T00:05:00Z"));
        verify(valueOperations)
                .set(
                        eq("zeromail:mkey:edit-session:" + actorId + ":OPENAI"),
                        eq("fixed-token"),
                        eq(Duration.ofMinutes(5)));
    }

    @Test
    void consume_uses_get_delete_semantics_and_rejects_mismatch() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000111");
        when(valueOperations.getAndDelete("zeromail:mkey:edit-session:" + actorId + ":OPENAI"))
                .thenReturn("expected-token");
        MasterKeyEditSessionService editSessionService =
                new MasterKeyEditSessionService(() -> redisTemplate, () -> "unused", fixedClock());

        assertThat(editSessionService.consume(actorId, LlmProvider.OPENAI, "wrong-token"))
                .isEmpty();
        assertThat(editSessionService.consume(actorId, LlmProvider.OPENAI, "expected-token"))
                .isEqualTo(Optional.of("expected-token"));
    }

    private static java.time.Clock fixedClock() {
        return java.time.Clock.fixed(
                Instant.parse("2026-05-20T00:00:00Z"), java.time.ZoneOffset.UTC);
    }
}
