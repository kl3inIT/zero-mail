package com.zeromail.core.rules.service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.zeromail.core.gmail.model.GmailConnectionStatus;
import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.gmail.service.GmailPreviewReadService;
import com.zeromail.core.rules.model.GmailPreviewUnavailableException;
import com.zeromail.core.rules.model.MatcherNode;
import com.zeromail.core.rules.model.PreviewSampleSize;
import com.zeromail.core.rules.model.RuleEvaluationInput;

@Service
public class RulePreviewDataService {

  static final Duration DEFAULT_FETCH_BUDGET = Duration.ofSeconds(5);

  private final GmailConnectionService gmailConnectionService;
  private final GmailPreviewReadService gmailPreviewReadService;

  public RulePreviewDataService(
      GmailConnectionService gmailConnectionService,
      GmailPreviewReadService gmailPreviewReadService) {
    this.gmailConnectionService =
        Objects.requireNonNull(gmailConnectionService, "gmailConnectionService must not be null");
    this.gmailPreviewReadService =
        Objects.requireNonNull(gmailPreviewReadService, "gmailPreviewReadService must not be null");
  }

  public List<PreviewInput> fetchPreviewInputs(
      UUID tenantId, MatcherNode matcherNode, PreviewSampleSize sampleSize) {
    Objects.requireNonNull(matcherNode, "matcherNode must not be null");
    return fetchPreviewInputs(tenantId, matcherNode.requiresBodyEvidence(), sampleSize);
  }

  List<PreviewInput> fetchPreviewInputs(
      UUID tenantId, boolean requiresBodyEvidence, PreviewSampleSize sampleSize) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(sampleSize, "sampleSize must not be null");
    GmailConnectionStatus connectionStatus =
        GmailConnectionStatus.fromId(gmailConnectionService.currentStatus(tenantId).status());
    if (connectionStatus == GmailConnectionStatus.NOT_CONNECTED) {
      throw new GmailPreviewUnavailableException(
          GmailPreviewUnavailableException.Reason.NOT_CONNECTED);
    }
    if (connectionStatus != GmailConnectionStatus.CONNECTED) {
      throw new GmailPreviewUnavailableException(
          GmailPreviewUnavailableException.Reason.DISCONNECTED);
    }

    try {
      return gmailPreviewReadService
          .fetchRecentMessages(
              tenantId, sampleSize.value(), requiresBodyEvidence, DEFAULT_FETCH_BUDGET)
          .stream()
          .map(RulePreviewDataService::toPreviewInput)
          .toList();
    } catch (
        GmailPreviewReadService.GmailPreviewReadUnavailableException readUnavailableException) {
      throw new GmailPreviewUnavailableException(mapReason(readUnavailableException.reason()));
    }
  }

  private static PreviewInput toPreviewInput(
      GmailPreviewReadService.GmailPreviewMessage gmailPreviewMessage) {
    RuleEvaluationInput ruleEvaluationInput =
        new RuleEvaluationInput(
            gmailPreviewMessage.sanitizedSenderEmail(),
            gmailPreviewMessage.sanitizedSenderDomain(),
            gmailPreviewMessage.sanitizedToRecipientEmails(),
            gmailPreviewMessage.sanitizedCcRecipientEmails(),
            gmailPreviewMessage.sanitizedSubjectExcerpt(),
            gmailPreviewMessage.gmailLabelIds(),
            gmailPreviewMessage.gmailCategories(),
            gmailPreviewMessage.internalDate(),
            gmailPreviewMessage.observedAt(),
            gmailPreviewMessage.hasAttachment(),
            gmailPreviewMessage.listUnsubscribePresent(),
            gmailPreviewMessage.newsletterIndicatorPresent(),
            gmailPreviewMessage.sanitizedBodyEvidencePresent(),
            gmailPreviewMessage.bodyDerivedFlags());
    return new PreviewInput(
        gmailPreviewMessage.gmailMessageId(),
        gmailPreviewMessage.gmailThreadId(),
        new SafeMessageSummary(
            gmailPreviewMessage.sanitizedSenderEmail(),
            gmailPreviewMessage.sanitizedSenderDomain(),
            gmailPreviewMessage.sanitizedSubjectExcerpt(),
            gmailPreviewMessage.internalDate(),
            gmailPreviewMessage.gmailLabelIds()),
        ruleEvaluationInput);
  }

  private static GmailPreviewUnavailableException.Reason mapReason(
      GmailPreviewReadService.UnavailableReason unavailableReason) {
    return switch (unavailableReason) {
      case NOT_CONNECTED -> GmailPreviewUnavailableException.Reason.NOT_CONNECTED;
      case DISCONNECTED -> GmailPreviewUnavailableException.Reason.DISCONNECTED;
      case NO_READ_GRANT -> GmailPreviewUnavailableException.Reason.NO_READ_GRANT;
      case REVOKED -> GmailPreviewUnavailableException.Reason.REVOKED;
      case FETCH_TIMEOUT -> GmailPreviewUnavailableException.Reason.FETCH_TIMEOUT;
      case GMAIL_UNAVAILABLE -> GmailPreviewUnavailableException.Reason.GMAIL_UNAVAILABLE;
    };
  }

  public record PreviewInput(
      String gmailMessageId,
      String gmailThreadId,
      SafeMessageSummary summary,
      RuleEvaluationInput ruleEvaluationInput) {

    public PreviewInput {
      Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
      Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
      Objects.requireNonNull(summary, "summary must not be null");
      Objects.requireNonNull(ruleEvaluationInput, "ruleEvaluationInput must not be null");
    }
  }

  public record SafeMessageSummary(
      String sanitizedSenderEmail,
      String sanitizedSenderDomain,
      String sanitizedSubjectExcerpt,
      java.time.Instant internalDate,
      List<String> gmailLabelIds) {

    public SafeMessageSummary {
      sanitizedSenderEmail = Objects.requireNonNullElse(sanitizedSenderEmail, "");
      sanitizedSenderDomain = Objects.requireNonNullElse(sanitizedSenderDomain, "");
      sanitizedSubjectExcerpt = Objects.requireNonNullElse(sanitizedSubjectExcerpt, "");
      Objects.requireNonNull(internalDate, "internalDate must not be null");
      gmailLabelIds =
          List.copyOf(Objects.requireNonNull(gmailLabelIds, "gmailLabelIds must not be null"));
    }
  }
}
