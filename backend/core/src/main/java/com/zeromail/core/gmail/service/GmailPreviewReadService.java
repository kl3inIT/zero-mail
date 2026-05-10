package com.zeromail.core.gmail.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;

@Service
public class GmailPreviewReadService {

  private static final List<String> METADATA_HEADERS =
      List.of(
          "From",
          "To",
          "Cc",
          "Subject",
          "List-Unsubscribe",
          "List-Id",
          "Precedence",
          "Content-Type");
  private static final int SUBJECT_EXCERPT_MAX_LENGTH = 120;
  private static final String METADATA_FIELDS =
      "id,threadId,labelIds,internalDate,payload/headers,payload/parts/filename,"
          + "payload/parts/mimeType";
  private static final String FULL_FIELDS =
      "id,threadId,labelIds,internalDate,payload/headers,payload/body/size,"
          + "payload/parts(filename,mimeType,body/size,parts)";

  private final JdbcTemplate jdbcTemplate;
  private final GmailConnectionRepository gmailConnectionRepository;
  private final GmailApiClientFactory gmailApiClientFactory;
  private final RefreshTokenCipher refreshTokenCipher;
  private final Clock clock;

  @Autowired
  public GmailPreviewReadService(
      JdbcTemplate jdbcTemplate,
      GmailConnectionRepository gmailConnectionRepository,
      GmailApiClientFactory gmailApiClientFactory,
      RefreshTokenCipher refreshTokenCipher) {
    this(
        jdbcTemplate,
        gmailConnectionRepository,
        gmailApiClientFactory,
        refreshTokenCipher,
        Clock.systemUTC());
  }

  GmailPreviewReadService(
      JdbcTemplate jdbcTemplate,
      GmailConnectionRepository gmailConnectionRepository,
      GmailApiClientFactory gmailApiClientFactory,
      RefreshTokenCipher refreshTokenCipher,
      Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.gmailConnectionRepository = gmailConnectionRepository;
    this.gmailApiClientFactory = gmailApiClientFactory;
    this.refreshTokenCipher = refreshTokenCipher;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<GmailPreviewMessage> fetchRecentMessages(
      UUID tenantId, int sampleSize, boolean includeBodyEvidence, Duration fetchBudget) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(fetchBudget, "fetchBudget must not be null");
    List<ObservedPreviewMessage> observedMessages =
        findRecentObservedMessages(tenantId, sampleSize);
    if (observedMessages.isEmpty()) {
      return List.of();
    }

    GmailConnectionEntity connection =
        gmailConnectionRepository
            .findByTenantId(tenantId)
            .orElseThrow(
                () -> new GmailPreviewReadUnavailableException(UnavailableReason.NOT_CONNECTED));
    if (connection.getStatus() != GmailConnectionStatus.CONNECTED) {
      throw new GmailPreviewReadUnavailableException(UnavailableReason.DISCONNECTED);
    }
    if (connection.getRefreshTokenEncrypted() == null) {
      throw new GmailPreviewReadUnavailableException(UnavailableReason.NO_READ_GRANT);
    }

    try {
      String decryptedRefreshToken =
          new String(
              refreshTokenCipher.decrypt(
                  connection.getRefreshTokenEncrypted(), tenantId.toString()),
              StandardCharsets.UTF_8);
      GmailApiClientFactory.TokenRefreshResult tokenResult =
          gmailApiClientFactory.refreshAccessToken(decryptedRefreshToken);
      Gmail gmail = gmailApiClientFactory.buildGmailClient(tokenResult.accessToken().value());
      return fetchMessagesWithinBudget(gmail, observedMessages, includeBodyEvidence, fetchBudget);
    } catch (InvalidGrantException invalidGrantException) {
      throw new GmailPreviewReadUnavailableException(UnavailableReason.REVOKED);
    } catch (GoogleJsonResponseException googleResponseException) {
      if (googleResponseException.getStatusCode() == 401
          || googleResponseException.getStatusCode() == 403) {
        throw new GmailPreviewReadUnavailableException(UnavailableReason.NO_READ_GRANT);
      }
      throw new GmailPreviewReadUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
    } catch (IOException ioException) {
      throw new GmailPreviewReadUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
    }
  }

  private List<ObservedPreviewMessage> findRecentObservedMessages(UUID tenantId, int sampleSize) {
    return jdbcTemplate.query(
        """
        select gmail_message_id, gmail_thread_id, label_ids, internal_date, observed_at
        from mail_message_observed
        where tenant_id = ?
        order by internal_date desc nulls last, observed_at desc
        limit ?
        """,
        (resultSet, rowNumber) ->
            new ObservedPreviewMessage(
                resultSet.getString("gmail_message_id"),
                resultSet.getString("gmail_thread_id"),
                (String[]) resultSet.getArray("label_ids").getArray(),
                resultSet.getObject("internal_date", Long.class),
                resultSet.getTimestamp("observed_at").toInstant()),
        tenantId,
        sampleSize);
  }

  private List<GmailPreviewMessage> fetchMessagesWithinBudget(
      Gmail gmail,
      List<ObservedPreviewMessage> observedMessages,
      boolean includeBodyEvidence,
      Duration fetchBudget)
      throws IOException {
    Instant deadline = clock.instant().plus(fetchBudget);
    List<Message> gmailMessages;
    if (observedMessages.size() > 1) {
      try {
        gmailMessages =
            fetchMessagesWithBatch(gmail, observedMessages, includeBodyEvidence, deadline);
      } catch (IOException | RuntimeException batchException) {
        gmailMessages =
            fetchMessagesSequentially(gmail, observedMessages, includeBodyEvidence, deadline);
      }
    } else {
      gmailMessages =
          fetchMessagesSequentially(gmail, observedMessages, includeBodyEvidence, deadline);
    }
    return mergeObservedAndFetchedMessages(observedMessages, gmailMessages, includeBodyEvidence);
  }

  private List<Message> fetchMessagesWithBatch(
      Gmail gmail,
      List<ObservedPreviewMessage> observedMessages,
      boolean includeBodyEvidence,
      Instant deadline)
      throws IOException {
    BatchRequest batchRequest = gmail.batch();
    LinkedHashMap<String, Message> messagesById = new LinkedHashMap<>();
    JsonBatchCallback<Message> callback =
        new JsonBatchCallback<>() {
          @Override
          public void onSuccess(Message message, HttpHeaders responseHeaders) {
            if (message != null && message.getId() != null) {
              messagesById.put(message.getId(), message);
            }
          }

          @Override
          public void onFailure(GoogleJsonError googleJsonError, HttpHeaders responseHeaders) {
            // Missing individual messages fall back to the observed metadata row below.
          }
        };

    for (ObservedPreviewMessage observedMessage : observedMessages) {
      assertWithinBudget(deadline);
      messageGetRequest(gmail, observedMessage.gmailMessageId(), includeBodyEvidence)
          .queue(batchRequest, callback);
    }
    batchRequest.execute();

    ArrayList<Message> orderedMessages = new ArrayList<>();
    for (ObservedPreviewMessage observedMessage : observedMessages) {
      Message message = messagesById.get(observedMessage.gmailMessageId());
      if (message != null) {
        orderedMessages.add(message);
      }
    }
    return List.copyOf(orderedMessages);
  }

  private List<Message> fetchMessagesSequentially(
      Gmail gmail,
      List<ObservedPreviewMessage> observedMessages,
      boolean includeBodyEvidence,
      Instant deadline)
      throws IOException {
    ArrayList<Message> gmailMessages = new ArrayList<>();
    for (ObservedPreviewMessage observedMessage : observedMessages) {
      assertWithinBudget(deadline);
      gmailMessages.add(
          messageGetRequest(gmail, observedMessage.gmailMessageId(), includeBodyEvidence)
              .execute());
    }
    return List.copyOf(gmailMessages);
  }

  private static Gmail.Users.Messages.Get messageGetRequest(
      Gmail gmail, String gmailMessageId, boolean includeBodyEvidence) throws IOException {
    Gmail.Users.Messages.Get messageGetRequest =
        gmail
            .users()
            .messages()
            .get("me", gmailMessageId)
            .setFormat(includeBodyEvidence ? "full" : "metadata")
            .setFields(includeBodyEvidence ? FULL_FIELDS : METADATA_FIELDS);
    if (!includeBodyEvidence) {
      messageGetRequest.setMetadataHeaders(METADATA_HEADERS);
    }
    return messageGetRequest;
  }

  private List<GmailPreviewMessage> mergeObservedAndFetchedMessages(
      List<ObservedPreviewMessage> observedMessages,
      List<Message> gmailMessages,
      boolean includeBodyEvidence) {
    Map<String, Message> messagesById = new LinkedHashMap<>();
    for (Message gmailMessage : gmailMessages) {
      if (gmailMessage != null && gmailMessage.getId() != null) {
        messagesById.put(gmailMessage.getId(), gmailMessage);
      }
    }

    ArrayList<GmailPreviewMessage> previewMessages = new ArrayList<>();
    for (ObservedPreviewMessage observedMessage : observedMessages) {
      Message gmailMessage = messagesById.get(observedMessage.gmailMessageId());
      previewMessages.add(toPreviewMessage(observedMessage, gmailMessage, includeBodyEvidence));
    }
    return List.copyOf(previewMessages);
  }

  private GmailPreviewMessage toPreviewMessage(
      ObservedPreviewMessage observedMessage, Message gmailMessage, boolean includeBodyEvidence) {
    MessagePart payload = gmailMessage == null ? null : gmailMessage.getPayload();
    List<String> labelIds =
        gmailMessage == null || gmailMessage.getLabelIds() == null
            ? List.of(observedMessage.labelIds())
            : List.copyOf(gmailMessage.getLabelIds());
    String fromHeader = headerValue(payload, "From").orElse("");
    String senderEmail = sanitizeEmail(extractEmailAddress(fromHeader));
    String senderDomain =
        senderEmail.contains("@") ? senderEmail.substring(senderEmail.indexOf('@') + 1) : "";
    String subjectExcerpt = excerpt(headerValue(payload, "Subject").orElse(""));
    List<String> toRecipients = parseRecipients(headerValue(payload, "To").orElse(""));
    List<String> ccRecipients = parseRecipients(headerValue(payload, "Cc").orElse(""));
    boolean hasAttachment = hasAttachment(payload);
    boolean listUnsubscribePresent = headerValue(payload, "List-Unsubscribe").isPresent();
    boolean newsletterIndicatorPresent =
        listUnsubscribePresent
            || headerValue(payload, "List-Id").isPresent()
            || headerValue(payload, "Precedence")
                .map(
                    value -> Set.of("bulk", "list").contains(value.trim().toLowerCase(Locale.ROOT)))
                .orElse(false);
    Optional<Boolean> sanitizedBodyEvidencePresent =
        includeBodyEvidence ? Optional.of(hasBodyEvidence(payload)) : Optional.empty();
    Set<String> bodyDerivedFlags =
        includeBodyEvidence && sanitizedBodyEvidencePresent.orElse(false)
            ? Set.of("body_evidence_present")
            : Set.of();

    return new GmailPreviewMessage(
        observedMessage.gmailMessageId(),
        Objects.requireNonNullElse(
            gmailMessage == null ? null : gmailMessage.getThreadId(),
            observedMessage.gmailThreadId()),
        senderEmail,
        senderDomain,
        toRecipients,
        ccRecipients,
        subjectExcerpt,
        labelIds,
        gmailCategories(labelIds),
        toInstant(
            gmailMessage == null ? observedMessage.internalDate() : gmailMessage.getInternalDate()),
        observedMessage.observedAt(),
        hasAttachment,
        listUnsubscribePresent,
        newsletterIndicatorPresent,
        sanitizedBodyEvidencePresent,
        bodyDerivedFlags);
  }

  private static Optional<String> headerValue(MessagePart payload, String headerName) {
    if (payload == null || payload.getHeaders() == null) {
      return Optional.empty();
    }
    return payload.getHeaders().stream()
        .filter(header -> headerName.equalsIgnoreCase(header.getName()))
        .map(MessagePartHeader::getValue)
        .filter(Objects::nonNull)
        .findFirst();
  }

  private static List<String> parseRecipients(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      return List.of();
    }
    ArrayList<String> recipients = new ArrayList<>();
    for (String part : headerValue.split(",")) {
      String emailAddress = sanitizeEmail(extractEmailAddress(part));
      if (!emailAddress.isBlank()) {
        recipients.add(emailAddress);
      }
    }
    return List.copyOf(recipients);
  }

  private static String extractEmailAddress(String headerValue) {
    if (headerValue == null) {
      return "";
    }
    int openAngleIndex = headerValue.indexOf('<');
    int closeAngleIndex = headerValue.indexOf('>');
    if (openAngleIndex >= 0 && closeAngleIndex > openAngleIndex) {
      return headerValue.substring(openAngleIndex + 1, closeAngleIndex);
    }
    return headerValue;
  }

  private static String sanitizeEmail(String emailAddress) {
    return sanitizedText(emailAddress).toLowerCase(Locale.ROOT);
  }

  private static String excerpt(String subject) {
    String sanitizedSubject = sanitizedText(subject);
    if (sanitizedSubject.length() <= SUBJECT_EXCERPT_MAX_LENGTH) {
      return sanitizedSubject;
    }
    return sanitizedSubject.substring(0, SUBJECT_EXCERPT_MAX_LENGTH).trim();
  }

  private static String sanitizedText(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    return text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").replaceAll("\\s+", " ").trim();
  }

  private static List<String> gmailCategories(List<String> labelIds) {
    LinkedHashSet<String> categories = new LinkedHashSet<>();
    for (String labelId : labelIds) {
      if (labelId != null && labelId.startsWith("CATEGORY_")) {
        categories.add(labelId.substring("CATEGORY_".length()).toLowerCase(Locale.ROOT));
      }
    }
    return List.copyOf(categories);
  }

  private static boolean hasAttachment(MessagePart payload) {
    if (payload == null || payload.getParts() == null) {
      return false;
    }
    for (MessagePart part : payload.getParts()) {
      if (part.getFilename() != null && !part.getFilename().isBlank()) {
        return true;
      }
      if (hasAttachment(part)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasBodyEvidence(MessagePart payload) {
    if (payload == null) {
      return false;
    }
    if (payload.getBody() != null
        && payload.getBody().getSize() != null
        && payload.getBody().getSize() > 0) {
      return true;
    }
    if (payload.getParts() == null) {
      return false;
    }
    for (MessagePart part : payload.getParts()) {
      if (hasBodyEvidence(part)) {
        return true;
      }
    }
    return false;
  }

  private static Instant toInstant(Long internalDateMillis) {
    return internalDateMillis == null ? Instant.EPOCH : Instant.ofEpochMilli(internalDateMillis);
  }

  private void assertWithinBudget(Instant deadline) {
    if (clock.instant().isAfter(deadline)) {
      throw new GmailPreviewReadUnavailableException(UnavailableReason.FETCH_TIMEOUT);
    }
  }

  private record ObservedPreviewMessage(
      String gmailMessageId,
      String gmailThreadId,
      String[] labelIds,
      Long internalDate,
      Instant observedAt) {}

  public record GmailPreviewMessage(
      String gmailMessageId,
      String gmailThreadId,
      String sanitizedSenderEmail,
      String sanitizedSenderDomain,
      List<String> sanitizedToRecipientEmails,
      List<String> sanitizedCcRecipientEmails,
      String sanitizedSubjectExcerpt,
      List<String> gmailLabelIds,
      List<String> gmailCategories,
      Instant internalDate,
      Instant observedAt,
      boolean hasAttachment,
      boolean listUnsubscribePresent,
      boolean newsletterIndicatorPresent,
      Optional<Boolean> sanitizedBodyEvidencePresent,
      Set<String> bodyDerivedFlags) {

    public GmailPreviewMessage {
      Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
      Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
      sanitizedToRecipientEmails =
          List.copyOf(
              Objects.requireNonNull(
                  sanitizedToRecipientEmails, "sanitizedToRecipientEmails must not be null"));
      sanitizedCcRecipientEmails =
          List.copyOf(
              Objects.requireNonNull(
                  sanitizedCcRecipientEmails, "sanitizedCcRecipientEmails must not be null"));
      gmailLabelIds =
          List.copyOf(Objects.requireNonNull(gmailLabelIds, "gmailLabelIds must not be null"));
      gmailCategories =
          List.copyOf(Objects.requireNonNull(gmailCategories, "gmailCategories must not be null"));
      Objects.requireNonNull(internalDate, "internalDate must not be null");
      Objects.requireNonNull(observedAt, "observedAt must not be null");
      sanitizedBodyEvidencePresent =
          Objects.requireNonNullElseGet(sanitizedBodyEvidencePresent, Optional::empty);
      bodyDerivedFlags =
          Set.copyOf(Objects.requireNonNull(bodyDerivedFlags, "bodyDerivedFlags must not be null"));
    }
  }

  public enum UnavailableReason {
    NOT_CONNECTED,
    DISCONNECTED,
    NO_READ_GRANT,
    REVOKED,
    FETCH_TIMEOUT,
    GMAIL_UNAVAILABLE
  }

  public static class GmailPreviewReadUnavailableException extends RuntimeException {

    private final UnavailableReason reason;

    public GmailPreviewReadUnavailableException(UnavailableReason reason) {
      super("Gmail preview read unavailable: " + reason.name());
      this.reason = reason;
    }

    public UnavailableReason reason() {
      return reason;
    }
  }
}
