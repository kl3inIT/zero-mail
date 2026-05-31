package com.zeromail.core.chat.usecases.settings;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.byok.ByokRateLimiter;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceGenerationService {

    static final int MAX_GENERATED_STYLE_WORDS = 500;

    private static final String RATE_LIMIT_KEY = "voice.generate";
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final int RATE_LIMIT_MAX_REQUESTS = 3;
    private static final int MAX_STYLE_TOKENS = 700;
    private static final Logger log = LoggerFactory.getLogger(VoiceGenerationService.class);

    private final GmailSentMessagesReader gmailSentMessagesReader;
    private final LlmGateway llmGateway;
    private final ByokRateLimiter byokRateLimiter;

    public VoiceGenerationService(
            GmailSentMessagesReader gmailSentMessagesReader,
            LlmGateway llmGateway,
            ByokRateLimiter byokRateLimiter) {
        this.gmailSentMessagesReader =
                Objects.requireNonNull(gmailSentMessagesReader, "gmailSentMessagesReader");
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway");
        this.byokRateLimiter = Objects.requireNonNull(byokRateLimiter, "byokRateLimiter");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VoiceGenerationResult generate(UUID tenantId, int sampleSize) {
        return generate(new VoiceGenerationCommand(tenantId, sampleSize));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VoiceGenerationResult generate(VoiceGenerationCommand command) {
        Objects.requireNonNull(command, "command");
        UUID tenantId = command.tenantId();
        byokRateLimiter.requireAllowance(
                tenantId, RATE_LIMIT_KEY, RATE_LIMIT_MAX_REQUESTS, RATE_LIMIT_WINDOW);

        List<GmailSentMessagesReader.SentMessageSummary> samples =
                gmailSentMessagesReader.readRecentSent(tenantId, command.sampleSize());
        if (samples.isEmpty()) {
            return VoiceGenerationResult.empty();
        }

        String prompt = VoiceGenerationPrompt.assemble(samples);
        String generatedStyle;
        try {
            generatedStyle =
                    llmGateway.generatePreviewText(
                            CallSite.PREVIEW,
                            VoiceGenerationPrompt.SYSTEM_PROMPT,
                            prompt,
                            MAX_STYLE_TOKENS);
        } catch (RuntimeException generationFailure) {
            log.warn(
                    "event=voice.generate.failed tenantId={} reason={}",
                    tenantId,
                    generationFailure.getClass().getSimpleName());
            throw new VoiceGenerationFailedException(generationFailure);
        }

        String boundedStyle = truncateToWords(generatedStyle, MAX_GENERATED_STYLE_WORDS);
        log.info(
                "event=voice.generate.completed tenantId={} sampleSize={} resultWordCount={}",
                tenantId,
                samples.size(),
                countWords(boundedStyle));
        return new VoiceGenerationResult(boundedStyle);
    }

    static String truncateToWords(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] words = text.strip().split("\\s+");
        if (words.length <= maxWords) {
            return text.strip();
        }
        return String.join(" ", Arrays.copyOf(words, maxWords));
    }

    private static int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.strip().split("\\s+").length;
    }

    public static class VoiceGenerationFailedException extends BusinessException {

        public VoiceGenerationFailedException(Throwable cause) {
            super("Voice generation failed", cause);
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.GATEWAY_FAILURE;
        }

        @Override
        public String errorCode() {
            return "voice.generate.failed";
        }

        @Override
        public String logEvent() {
            return "voice_generate_failed";
        }

        @Override
        public String title() {
            return "Voice generation failed";
        }

        @Override
        public String detail() {
            return "Writing style could not be generated from recent sent messages.";
        }
    }
}
