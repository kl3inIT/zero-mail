package com.zeromail.core.draft.usecases;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.zeromail.core.draft.domain.ToneContext;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.llm.exception.SanitizationException;
import com.zeromail.core.llm.exception.TokenBudgetExceededException;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.tenant.TenantContext;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ToneContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ToneContextBuilder.class);
    private static final int MAX_SENT_MAIL_MESSAGES = 8;
    private static final int MAX_STYLE_SNIPPETS = 3;
    private static final Duration FETCH_BUDGET = Duration.ofSeconds(2);
    private static final Pattern QUOTE_INTRO_PATTERN =
            Pattern.compile("(?i)^on\\s+.+\\s+wrote:\\s*$");
    private static final Pattern CONTRACTION_PATTERN = Pattern.compile("(?i)\\b\\w+'\\w+\\b");

    private final SentMailSource sentMailSource;
    private final SanitizationPipeline sanitizationPipeline;
    private final Clock clock;

    @Autowired
    public ToneContextBuilder(
            GmailApiClientFactory gmailApiClientFactory,
            SanitizationPipeline sanitizationPipeline) {
        this(
                new GmailSentMailSource(gmailApiClientFactory, Clock.systemUTC()),
                sanitizationPipeline,
                Clock.systemUTC());
    }

    public ToneContextBuilder(
            SentMailSource sentMailSource, SanitizationPipeline sanitizationPipeline, Clock clock) {
        this.sentMailSource =
                Objects.requireNonNull(sentMailSource, "sentMailSource must not be null");
        this.sanitizationPipeline =
                Objects.requireNonNull(
                        sanitizationPipeline, "sanitizationPipeline must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ToneContext buildForCurrentTenant() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        try {
            List<String> rawSentMailBodies =
                    sentMailSource.fetchRecentSentMailBodies(
                            tenantId, MAX_SENT_MAIL_MESSAGES, FETCH_BUDGET);
            ToneContext toneContext = buildFromRawBodies(rawSentMailBodies);
            log.info(
                    "event=tone_context_built tenantId={} snippetCount={}",
                    tenantId,
                    toneContext.styleSnippets().size());
            return toneContext;
        } catch (IOException | GmailToneFetchException toneFetchFailure) {
            log.info(
                    "event=tone_context_unavailable tenantId={} reason={}",
                    tenantId,
                    toneFetchFailure.getClass().getSimpleName());
            return ToneContext.empty();
        }
    }

    private ToneContext buildFromRawBodies(List<String> rawSentMailBodies) {
        List<String> strippedBodies =
                rawSentMailBodies.stream()
                        .map(ToneContextBuilder::stripQuotesAndSignature)
                        .filter(strippedBody -> !strippedBody.isBlank())
                        .toList();
        String descriptorBlock = descriptorBlock(strippedBodies);
        ArrayList<String> styleSnippets = new ArrayList<>();
        for (String strippedBody : strippedBodies) {
            if (styleSnippets.size() == MAX_STYLE_SNIPPETS) {
                break;
            }
            try {
                SanitizationContext sanitizedSnippet = sanitizationPipeline.sanitize(strippedBody);
                if (!sanitizedSnippet.content().isBlank()) {
                    styleSnippets.add(sanitizedSnippet.content());
                }
            } catch (TokenBudgetExceededException | SanitizationException sanitizationFailure) {
                return new ToneContext(descriptorBlock, List.of());
            }
        }
        return new ToneContext(descriptorBlock, styleSnippets);
    }

    static String stripQuotesAndSignature(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "";
        }
        String normalizedBody = rawBody.replace("\r\n", "\n").replace('\r', '\n');
        ArrayList<String> keptLines = new ArrayList<>();
        for (String line : normalizedBody.split("\n")) {
            String trimmedLine = line.trim();
            if ("--".equals(trimmedLine) || "-- ".equals(line)) {
                break;
            }
            if (QUOTE_INTRO_PATTERN.matcher(trimmedLine).matches()) {
                break;
            }
            if (trimmedLine.startsWith(">")) {
                continue;
            }
            keptLines.add(line.stripTrailing());
        }
        return String.join("\n", keptLines).trim();
    }

    private static String descriptorBlock(List<String> strippedBodies) {
        if (strippedBodies.isEmpty()) {
            return "";
        }
        int wordCount = 0;
        int sentenceCount = 0;
        int messageCount = strippedBodies.size();
        boolean greetingPresent = false;
        boolean signOffPresent = false;
        boolean bulletUsagePresent = false;
        boolean contractionPresent = false;
        boolean emojiPresent = false;
        for (String strippedBody : strippedBodies) {
            String lowerCaseBody = strippedBody.toLowerCase(Locale.ROOT);
            wordCount += wordCount(strippedBody);
            sentenceCount += Math.max(1, strippedBody.split("[.!?]+").length);
            greetingPresent |=
                    lowerCaseBody.startsWith("hi ")
                            || lowerCaseBody.startsWith("hello ")
                            || lowerCaseBody.startsWith("hey ");
            signOffPresent |=
                    lowerCaseBody.contains("\nbest")
                            || lowerCaseBody.contains("\nthanks")
                            || lowerCaseBody.contains("\nregards");
            bulletUsagePresent |=
                    strippedBody
                            .lines()
                            .anyMatch(
                                    line ->
                                            line.stripLeading().startsWith("- ")
                                                    || line.stripLeading().startsWith("* "));
            contractionPresent |= CONTRACTION_PATTERN.matcher(strippedBody).find();
            emojiPresent |=
                    strippedBody
                            .codePoints()
                            .anyMatch(
                                    codePoint ->
                                            codePoint > 127
                                                    && Character.getType(codePoint)
                                                            == Character.OTHER_SYMBOL);
        }
        int averageMessageWords = Math.max(1, wordCount / messageCount);
        int averageSentenceWords = Math.max(1, wordCount / Math.max(1, sentenceCount));
        return String.join(
                "\n",
                "sampleCount=" + messageCount,
                "averageMessageWords=" + averageMessageWords,
                "averageSentenceWords=" + averageSentenceWords,
                "greetingPresent=" + greetingPresent,
                "signOffPresent=" + signOffPresent,
                "bulletUsagePresent=" + bulletUsagePresent,
                "contractionPresent=" + contractionPresent,
                "emojiPresent=" + emojiPresent);
    }

    private static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    public interface SentMailSource {

        List<String> fetchRecentSentMailBodies(UUID tenantId, int maxMessages, Duration fetchBudget)
                throws IOException;
    }

    private static final class GmailSentMailSource implements SentMailSource {

        private static final String USER_ID = "me";
        private static final String SENT_LABEL_ID = "SENT";
        private static final String FULL_FIELDS =
                "id,payload/mimeType,payload/body/data,payload/parts(mimeType,body/data,parts)";

        private final GmailApiClientFactory gmailApiClientFactory;
        private final Clock clock;

        private GmailSentMailSource(GmailApiClientFactory gmailApiClientFactory, Clock clock) {
            this.gmailApiClientFactory =
                    Objects.requireNonNull(
                            gmailApiClientFactory, "gmailApiClientFactory must not be null");
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
        }

        @Override
        public List<String> fetchRecentSentMailBodies(
                UUID tenantId, int maxMessages, Duration fetchBudget) throws IOException {
            Instant deadline = clock.instant().plus(fetchBudget);
            try {
                Gmail gmail = gmailApiClientFactory.buildClientForTenant(tenantId, fetchBudget);
                ListMessagesResponse response =
                        gmail.users()
                                .messages()
                                .list(USER_ID)
                                .setLabelIds(List.of(SENT_LABEL_ID))
                                .setMaxResults((long) maxMessages)
                                .execute();
                List<Message> messageReferences = response.getMessages();
                if (messageReferences == null || messageReferences.isEmpty()) {
                    return List.of();
                }
                ArrayList<String> sentMailBodies = new ArrayList<>();
                for (Message messageReference : messageReferences) {
                    assertWithinBudget(deadline);
                    Message fullMessage =
                            gmail.users()
                                    .messages()
                                    .get(USER_ID, messageReference.getId())
                                    .setFormat("full")
                                    .setFields(FULL_FIELDS)
                                    .execute();
                    String readableBody = extractReadableBody(fullMessage.getPayload());
                    if (!readableBody.isBlank()) {
                        sentMailBodies.add(readableBody);
                    }
                }
                return List.copyOf(sentMailBodies);
            } catch (RuntimeException gmailRuntimeFailure) {
                throw new GmailToneFetchException(gmailRuntimeFailure);
            }
        }

        private void assertWithinBudget(Instant deadline) {
            if (clock.instant().isAfter(deadline)) {
                throw new GmailToneFetchException();
            }
        }

        private static String extractReadableBody(MessagePart payload) {
            if (payload == null) {
                return "";
            }
            String directBody = GmailMimeDecoder.decodedBody(payload);
            if (!directBody.isBlank()
                    && GmailMimeDecoder.isReadableMimeType(payload.getMimeType())) {
                return directBody;
            }
            List<MessagePart> parts = payload.getParts();
            if (parts == null || parts.isEmpty()) {
                return directBody;
            }
            ArrayList<String> extractedParts = new ArrayList<>();
            for (MessagePart part : parts) {
                String extractedPart = extractReadableBody(part);
                if (!extractedPart.isBlank()) {
                    extractedParts.add(extractedPart);
                }
            }
            return String.join("\n", extractedParts);
        }
    }

    private static final class GmailToneFetchException extends RuntimeException {

        private GmailToneFetchException() {
            super();
        }

        private GmailToneFetchException(Throwable cause) {
            super(cause);
        }
    }
}
