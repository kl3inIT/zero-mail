package com.zeromail.core.chat.usecases.settings;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GmailSentMessagesReader {

    static final int MAX_SAMPLE_SIZE = 50;
    static final int MAX_BODY_CHARS_PER_SAMPLE = 4_000;
    static final int MAX_AGGREGATE_PROMPT_CHARS = 60_000;

    private static final Logger log = LoggerFactory.getLogger(GmailSentMessagesReader.class);

    private final GmailApiClientFactory gmailApiClientFactory;

    public GmailSentMessagesReader(GmailApiClientFactory gmailApiClientFactory) {
        this.gmailApiClientFactory = Objects.requireNonNull(gmailApiClientFactory);
    }

    public List<SentMessageSummary> readRecentSent(UUID tenantId, int sampleSize) {
        Objects.requireNonNull(tenantId, "tenantId");
        int boundedSampleSize = Math.max(0, Math.min(sampleSize, MAX_SAMPLE_SIZE));
        if (boundedSampleSize == 0) {
            return List.of();
        }
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(tenantId);
            ListMessagesResponse listMessagesResponse =
                    gmail.users()
                            .messages()
                            .list("me")
                            .setQ("in:sent")
                            .setMaxResults((long) boundedSampleSize)
                            .execute();
            List<Message> messageReferences = listMessagesResponse.getMessages();
            if (messageReferences == null || messageReferences.isEmpty()) {
                return List.of();
            }
            List<SentMessageSummary> samples = new ArrayList<>();
            for (Message messageReference : messageReferences) {
                Message message =
                        gmail.users()
                                .messages()
                                .get("me", messageReference.getId())
                                .setFormat("full")
                                .execute();
                samples.add(toSummary(message));
            }
            return capAggregate(tenantId, samples);
        } catch (IOException | RuntimeException gmailReadFailure) {
            throw new GmailSentReadException(gmailReadFailure);
        }
    }

    private static SentMessageSummary toSummary(Message message) {
        MessagePart payload = message.getPayload();
        String body =
                truncate(
                        QuotedReplyStripper.strip(extractBody(payload)), MAX_BODY_CHARS_PER_SAMPLE);
        return new SentMessageSummary(
                header(payload, "From"), header(payload, "To"), header(payload, "Subject"), body);
    }

    private static List<SentMessageSummary> capAggregate(
            UUID tenantId, List<SentMessageSummary> samples) {
        int originalSampleCount = samples.size();
        List<SentMessageSummary> cappedSamples = new ArrayList<>(samples);
        int aggregateChars = aggregateChars(cappedSamples);
        while (aggregateChars > MAX_AGGREGATE_PROMPT_CHARS && !cappedSamples.isEmpty()) {
            cappedSamples.remove(cappedSamples.size() - 1);
            aggregateChars = aggregateChars(cappedSamples);
        }
        if (cappedSamples.size() != originalSampleCount) {
            log.info(
                    "event=voice.generate.aggregate_cap_applied tenantId={} originalSampleCount={} cappedSampleCount={} aggregateChars={}",
                    tenantId,
                    originalSampleCount,
                    cappedSamples.size(),
                    aggregateChars);
        }
        return List.copyOf(cappedSamples);
    }

    private static int aggregateChars(List<SentMessageSummary> samples) {
        return samples.stream().mapToInt(sample -> sample.bodyPlaintext().length()).sum();
    }

    private static String extractBody(MessagePart messagePart) {
        return findPart(messagePart, "text/plain")
                .or(
                        () ->
                                findPart(messagePart, "text/html")
                                        .map(GmailSentMessagesReader::htmlToText))
                .orElse("");
    }

    private static Optional<String> findPart(MessagePart messagePart, String mimeType) {
        if (messagePart == null) {
            return Optional.empty();
        }
        if (mimeType.equalsIgnoreCase(messagePart.getMimeType()) && messagePart.getBody() != null) {
            String data = messagePart.getBody().getData();
            if (data != null && !data.isBlank()) {
                return Optional.of(decodeBase64Url(data));
            }
        }
        List<MessagePart> parts = messagePart.getParts();
        if (parts == null || parts.isEmpty()) {
            return Optional.empty();
        }
        return parts.stream()
                .map(part -> findPart(part, mimeType))
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .findFirst();
    }

    private static String decodeBase64Url(String data) {
        return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
    }

    private static String htmlToText(String html) {
        return html.replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .strip();
    }

    private static String header(MessagePart payload, String name) {
        if (payload == null || payload.getHeaders() == null) {
            return "";
        }
        return payload.getHeaders().stream()
                .filter(header -> name.equalsIgnoreCase(header.getName()))
                .map(MessagePartHeader::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalizedValue = value.strip();
        if (normalizedValue.length() <= maxChars) {
            return normalizedValue;
        }
        return normalizedValue.substring(0, maxChars);
    }

    public record SentMessageSummary(
            String fromAddress, String toAddress, String subject, String bodyPlaintext) {}

    public static class GmailSentReadException extends BusinessException {

        public GmailSentReadException(Throwable cause) {
            super("Gmail Sent messages could not be read", cause);
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.GATEWAY_FAILURE;
        }

        @Override
        public String errorCode() {
            return "voice.generate.gmail_read_failed";
        }

        @Override
        public String logEvent() {
            return "voice_generate_gmail_read_failed";
        }

        @Override
        public String title() {
            return "Gmail Sent messages unavailable";
        }

        @Override
        public String detail() {
            return "Recent sent messages could not be read for voice generation.";
        }
    }
}
