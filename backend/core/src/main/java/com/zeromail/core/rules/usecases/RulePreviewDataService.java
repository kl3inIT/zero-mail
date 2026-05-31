package com.zeromail.core.rules.usecases;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService;
import com.zeromail.core.rules.domain.MatcherNode;
import com.zeromail.core.rules.domain.PreviewSampleSize;
import com.zeromail.core.rules.domain.RuleEvaluationInput;
import com.zeromail.core.rules.exception.GmailPreviewUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RulePreviewDataService {

    static final Duration DEFAULT_FETCH_BUDGET = Duration.ofSeconds(5);

    private final GmailConnectionService gmailConnectionService;
    private final GmailPreviewReadService gmailPreviewReadService;

    public RulePreviewDataService(
            GmailConnectionService gmailConnectionService,
            GmailPreviewReadService gmailPreviewReadService) {
        this.gmailConnectionService =
                Objects.requireNonNull(
                        gmailConnectionService, "gmailConnectionService must not be null");
        this.gmailPreviewReadService =
                Objects.requireNonNull(
                        gmailPreviewReadService, "gmailPreviewReadService must not be null");
    }

    public List<PreviewInput> fetchPreviewInputs(
            UUID tenantId, MatcherNode matcherNode, PreviewSampleSize sampleSize) {
        Objects.requireNonNull(matcherNode, "matcherNode must not be null");
        return fetchPreviewInputs(tenantId, matcherNode.requiresBodyEvidence(), sampleSize);
    }

    public List<PreviewInput> fetchPreviewInputs(
            UUID tenantId, boolean requiresBodyEvidence, PreviewSampleSize sampleSize) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(sampleSize, "sampleSize must not be null");
        GmailConnectionStatus connectionStatus =
                GmailConnectionStatus.fromId(
                        gmailConnectionService.currentStatus(tenantId).status());
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
                    .fetchRecentInboxMessages(
                            tenantId,
                            sampleSize.value(),
                            requiresBodyEvidence,
                            DEFAULT_FETCH_BUDGET)
                    .stream()
                    .map(RulePreviewDataService::toPreviewInput)
                    .toList();
        } catch (
                GmailPreviewReadService.GmailPreviewReadUnavailableException
                        readUnavailableException) {
            throw new GmailPreviewUnavailableException(
                    mapReason(readUnavailableException.reason()));
        }
    }

    /**
     * Fetch a single recent message by id for the per-message test flow. Reuses the triage-input
     * read path (Gmail messages.get by id) so the rules test tab can evaluate one email at a time.
     * Throws {@link GmailPreviewUnavailableException} when the connection is unusable or the
     * message is gone, matching the sample-fetch contract.
     */
    public PreviewInput fetchPreviewInputById(
            UUID tenantId, String gmailMessageId, String gmailThreadId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
        GmailConnectionStatus connectionStatus =
                GmailConnectionStatus.fromId(
                        gmailConnectionService.currentStatus(tenantId).status());
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
                    .fetchTriageInput(
                            tenantId, gmailMessageId, gmailThreadId, java.time.Instant.now())
                    .map(RulePreviewDataService::toPreviewInput)
                    .orElseThrow(
                            () ->
                                    new GmailPreviewUnavailableException(
                                            GmailPreviewUnavailableException.Reason
                                                    .GMAIL_UNAVAILABLE));
        } catch (
                GmailPreviewReadService.GmailPreviewReadUnavailableException
                        readUnavailableException) {
            throw new GmailPreviewUnavailableException(
                    mapReason(readUnavailableException.reason()));
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
                    List.copyOf(
                            Objects.requireNonNull(
                                    gmailLabelIds, "gmailLabelIds must not be null"));
        }
    }
}
