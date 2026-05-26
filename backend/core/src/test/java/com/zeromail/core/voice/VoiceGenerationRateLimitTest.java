package com.zeromail.core.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.zeromail.core.chat.usecases.settings.GmailSentMessagesReader;
import com.zeromail.core.chat.usecases.settings.GmailSentMessagesReader.SentMessageSummary;
import com.zeromail.core.chat.usecases.settings.VoiceGenerationService;
import com.zeromail.core.chat.usecases.settings.VoiceGenerationService.VoiceGenerationFailedException;
import com.zeromail.core.llm.byok.ByokRateLimiter;
import com.zeromail.core.llm.usecases.LlmGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class VoiceGenerationRateLimitTest {

    @Test
    void emptySentFolderReturnsEmptyGeneratedStyle() {
        UUID tenantId = UUID.randomUUID();
        GmailSentMessagesReader gmailSentMessagesReader = mock(GmailSentMessagesReader.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        VoiceGenerationService voiceGenerationService =
                new VoiceGenerationService(
                        gmailSentMessagesReader, llmGateway, rateLimiterReturning(1L));
        given(gmailSentMessagesReader.readRecentSent(tenantId, 20)).willReturn(List.of());

        assertThat(voiceGenerationService.generate(tenantId, 20).generatedStyle()).isEmpty();
    }

    @Test
    void llmFailureReturnsVoiceGenerateFailedCode() {
        UUID tenantId = UUID.randomUUID();
        GmailSentMessagesReader gmailSentMessagesReader = mock(GmailSentMessagesReader.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        VoiceGenerationService voiceGenerationService =
                new VoiceGenerationService(
                        gmailSentMessagesReader, llmGateway, rateLimiterReturning(1L));
        given(gmailSentMessagesReader.readRecentSent(tenantId, 20))
                .willReturn(
                        List.of(
                                new SentMessageSummary(
                                        "founder@example.test",
                                        "vip@example.test",
                                        "Sample",
                                        "Concise user-authored sample.")));
        given(llmGateway.generatePreviewText(any(), anyString(), anyString(), anyInt()))
                .willThrow(new IllegalStateException("simulated"));

        assertThatThrownBy(() -> voiceGenerationService.generate(tenantId, 20))
                .isInstanceOf(VoiceGenerationFailedException.class)
                .satisfies(
                        thrown ->
                                assertThat(((VoiceGenerationFailedException) thrown).errorCode())
                                        .isEqualTo("voice.generate.failed"));
    }

    @Test
    void fourthVoiceGenerationWithinOneHourIsRejected() {
        UUID tenantId = UUID.randomUUID();
        GmailSentMessagesReader gmailSentMessagesReader = mock(GmailSentMessagesReader.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        VoiceGenerationService voiceGenerationService =
                new VoiceGenerationService(
                        gmailSentMessagesReader, llmGateway, rateLimiterReturning(1L, 2L, 3L, 4L));
        given(gmailSentMessagesReader.readRecentSent(tenantId, 20))
                .willReturn(
                        List.of(
                                new SentMessageSummary(
                                        "founder@example.test",
                                        "vip@example.test",
                                        "Sample",
                                        "Concise user-authored sample.")));
        given(llmGateway.generatePreviewText(any(), anyString(), anyString(), anyInt()))
                .willReturn("Concise and direct style guide.");

        assertThat(voiceGenerationService.generate(tenantId, 20).generatedStyle())
                .isEqualTo("Concise and direct style guide.");
        assertThat(voiceGenerationService.generate(tenantId, 20).generatedStyle())
                .isEqualTo("Concise and direct style guide.");
        assertThat(voiceGenerationService.generate(tenantId, 20).generatedStyle())
                .isEqualTo("Concise and direct style guide.");

        assertThatThrownBy(() -> voiceGenerationService.generate(tenantId, 20))
                .isInstanceOf(ByokRateLimiter.RateLimitExceededException.class)
                .satisfies(
                        thrown ->
                                assertThat(
                                                ((ByokRateLimiter.RateLimitExceededException)
                                                                thrown)
                                                        .errorCode())
                                        .isEqualTo("voice.generate.rate_limited"));
    }

    private static ByokRateLimiter rateLimiterReturning(Long... counts) {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        Long[] additionalCounts = Arrays.copyOfRange(counts, 1, counts.length);
        given(valueOperations.increment(anyString())).willReturn(counts[0], additionalCounts);
        given(stringRedisTemplate.expire(anyString(), any(java.time.Duration.class)))
                .willReturn(true);
        return new ByokRateLimiter(
                () -> stringRedisTemplate,
                Clock.fixed(Instant.parse("2026-05-26T00:00:00Z"), ZoneOffset.UTC));
    }
}
