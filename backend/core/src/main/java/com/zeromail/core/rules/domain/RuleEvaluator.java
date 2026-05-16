package com.zeromail.core.rules.domain;

import com.google.re2j.Pattern;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RuleEvaluator {

    private static final ConcurrentMap<String, Pattern> COMPILED_SUBJECT_REGEXES =
            new ConcurrentHashMap<>();

    public RuleEvaluationResult evaluate(
            MatcherNode matcherNode, RuleEvaluationInput ruleEvaluationInput) {
        return switch (matcherNode) {
            case MatcherNode.SenderEmailMatcher senderEmailMatcher ->
                    evaluateTextEquals(
                            senderEmailMatcher.nodeId(),
                            senderEmailMatcher.email(),
                            ruleEvaluationInput.sanitizedSenderEmail(),
                            "sender_email");
            case MatcherNode.SenderDomainMatcher senderDomainMatcher ->
                    evaluateTextEquals(
                            senderDomainMatcher.nodeId(),
                            senderDomainMatcher.domain(),
                            ruleEvaluationInput.sanitizedSenderDomain(),
                            "sender_domain");
            case MatcherNode.RecipientToMatcher recipientToMatcher ->
                    evaluateRecipient(
                            recipientToMatcher.nodeId(),
                            recipientToMatcher.email(),
                            ruleEvaluationInput.sanitizedToRecipientEmails(),
                            "recipient_to");
            case MatcherNode.RecipientCcMatcher recipientCcMatcher ->
                    evaluateRecipient(
                            recipientCcMatcher.nodeId(),
                            recipientCcMatcher.email(),
                            ruleEvaluationInput.sanitizedCcRecipientEmails(),
                            "recipient_cc");
            case MatcherNode.SubjectContainsMatcher subjectContainsMatcher ->
                    evaluateSubjectContains(subjectContainsMatcher, ruleEvaluationInput);
            case MatcherNode.SubjectEqualsMatcher subjectEqualsMatcher ->
                    evaluateTextEquals(
                            subjectEqualsMatcher.nodeId(),
                            subjectEqualsMatcher.text(),
                            ruleEvaluationInput.sanitizedSubjectExcerpt(),
                            "subject_equals");
            case MatcherNode.SubjectRegexMatcher subjectRegexMatcher ->
                    evaluateSubjectRegex(subjectRegexMatcher, ruleEvaluationInput);
            case MatcherNode.GmailLabelPresentMatcher gmailLabelPresentMatcher ->
                    terminal(
                            gmailLabelPresentMatcher.nodeId(),
                            ruleEvaluationInput.hasGmailLabel(gmailLabelPresentMatcher.labelId()),
                            "gmail_label_present");
            case MatcherNode.GmailLabelAbsentMatcher gmailLabelAbsentMatcher ->
                    terminal(
                            gmailLabelAbsentMatcher.nodeId(),
                            !ruleEvaluationInput.hasGmailLabel(gmailLabelAbsentMatcher.labelId()),
                            "gmail_label_absent");
            case MatcherNode.GmailCategoryPresentMatcher gmailCategoryPresentMatcher ->
                    terminal(
                            gmailCategoryPresentMatcher.nodeId(),
                            ruleEvaluationInput.hasGmailCategory(
                                    gmailCategoryPresentMatcher.category()),
                            "gmail_category_present");
            case MatcherNode.GmailCategoryAbsentMatcher gmailCategoryAbsentMatcher ->
                    terminal(
                            gmailCategoryAbsentMatcher.nodeId(),
                            !ruleEvaluationInput.hasGmailCategory(
                                    gmailCategoryAbsentMatcher.category()),
                            "gmail_category_absent");
            case MatcherNode.HasAttachmentMatcher hasAttachmentMatcher ->
                    terminal(
                            hasAttachmentMatcher.nodeId(),
                            ruleEvaluationInput.hasAttachment(),
                            "has_attachment");
            case MatcherNode.ListUnsubscribePresentMatcher listUnsubscribePresentMatcher ->
                    terminal(
                            listUnsubscribePresentMatcher.nodeId(),
                            ruleEvaluationInput.listUnsubscribePresent(),
                            "list_unsubscribe_present");
            case MatcherNode.NewsletterIndicatorMatcher newsletterIndicatorMatcher ->
                    terminal(
                            newsletterIndicatorMatcher.nodeId(),
                            ruleEvaluationInput.newsletterIndicatorPresent(),
                            "newsletter_indicator");
            case MatcherNode.MessageAgeMatcher messageAgeMatcher ->
                    evaluateMessageAge(messageAgeMatcher, ruleEvaluationInput);
            case MatcherNode.MessageDateMatcher messageDateMatcher ->
                    evaluateMessageDate(messageDateMatcher, ruleEvaluationInput);
            case MatcherNode.AllMatcher allMatcher -> evaluateAll(allMatcher, ruleEvaluationInput);
            case MatcherNode.AnyMatcher anyMatcher -> evaluateAny(anyMatcher, ruleEvaluationInput);
            case MatcherNode.NotMatcher notMatcher -> evaluateNot(notMatcher, ruleEvaluationInput);
            case SemanticIntentMatcher semanticIntentMatcher ->
                    RuleEvaluationResult.deferred(
                            semanticIntentMatcher.nodeId(), "semantic_intent_deferred");
        };
    }

    private RuleEvaluationResult evaluateSubjectContains(
            MatcherNode.SubjectContainsMatcher subjectContainsMatcher,
            RuleEvaluationInput ruleEvaluationInput) {
        String expectedText = normalized(subjectContainsMatcher.text());
        String actualSubject = normalized(ruleEvaluationInput.sanitizedSubjectExcerpt());
        return terminal(
                subjectContainsMatcher.nodeId(),
                actualSubject.contains(expectedText),
                "subject_contains");
    }

    private RuleEvaluationResult evaluateSubjectRegex(
            MatcherNode.SubjectRegexMatcher subjectRegexMatcher,
            RuleEvaluationInput ruleEvaluationInput) {
        Pattern compiledPattern =
                COMPILED_SUBJECT_REGEXES.computeIfAbsent(
                        subjectRegexMatcher.regexPattern(), Pattern::compile);
        boolean regexMatched =
                compiledPattern.matcher(ruleEvaluationInput.sanitizedSubjectExcerpt()).find();
        return terminal(subjectRegexMatcher.nodeId(), regexMatched, "subject_regex_re2j");
    }

    private RuleEvaluationResult evaluateMessageAge(
            MatcherNode.MessageAgeMatcher messageAgeMatcher,
            RuleEvaluationInput ruleEvaluationInput) {
        long messageAgeInDays =
                ChronoUnit.DAYS.between(
                        ruleEvaluationInput.internalDate(), ruleEvaluationInput.observedAt());
        boolean matched =
                switch (messageAgeMatcher.operator()) {
                    case OLDER_THAN_DAYS -> messageAgeInDays > messageAgeMatcher.days();
                    case NEWER_THAN_DAYS -> messageAgeInDays < messageAgeMatcher.days();
                };
        return terminal(messageAgeMatcher.nodeId(), matched, "message_age");
    }

    private RuleEvaluationResult evaluateMessageDate(
            MatcherNode.MessageDateMatcher messageDateMatcher,
            RuleEvaluationInput ruleEvaluationInput) {
        LocalDate messageDate =
                ruleEvaluationInput.internalDate().atZone(ZoneOffset.UTC).toLocalDate();
        boolean matched =
                switch (messageDateMatcher.operator()) {
                    case BEFORE -> messageDate.isBefore(messageDateMatcher.date());
                    case ON -> messageDate.isEqual(messageDateMatcher.date());
                    case AFTER -> messageDate.isAfter(messageDateMatcher.date());
                };
        return terminal(messageDateMatcher.nodeId(), matched, "message_date");
    }

    private RuleEvaluationResult evaluateAll(
            MatcherNode.AllMatcher allMatcher, RuleEvaluationInput ruleEvaluationInput) {
        ArrayList<RuleEvaluationResult> childResults = new ArrayList<>();
        MatcherEvaluationState status = MatcherEvaluationState.MATCHED;
        for (MatcherNode childMatcherNode : allMatcher.children()) {
            RuleEvaluationResult childResult = evaluate(childMatcherNode, ruleEvaluationInput);
            childResults.add(childResult);
            if (childResult.status() == MatcherEvaluationState.NOT_MATCHED) {
                status = MatcherEvaluationState.NOT_MATCHED;
            } else if (status == MatcherEvaluationState.MATCHED
                    && childResult.status() == MatcherEvaluationState.DEFERRED) {
                status = MatcherEvaluationState.DEFERRED;
            }
        }
        return RuleEvaluationResult.compose(status, childResults);
    }

    private RuleEvaluationResult evaluateAny(
            MatcherNode.AnyMatcher anyMatcher, RuleEvaluationInput ruleEvaluationInput) {
        ArrayList<RuleEvaluationResult> childResults = new ArrayList<>();
        boolean anyMatched = false;
        boolean anyDeferred = false;
        for (MatcherNode childMatcherNode : anyMatcher.children()) {
            RuleEvaluationResult childResult = evaluate(childMatcherNode, ruleEvaluationInput);
            childResults.add(childResult);
            anyMatched = anyMatched || childResult.status() == MatcherEvaluationState.MATCHED;
            anyDeferred = anyDeferred || childResult.status() == MatcherEvaluationState.DEFERRED;
        }
        MatcherEvaluationState status =
                anyMatched
                        ? MatcherEvaluationState.MATCHED
                        : anyDeferred
                                ? MatcherEvaluationState.DEFERRED
                                : MatcherEvaluationState.NOT_MATCHED;
        return RuleEvaluationResult.compose(status, childResults);
    }

    private RuleEvaluationResult evaluateNot(
            MatcherNode.NotMatcher notMatcher, RuleEvaluationInput ruleEvaluationInput) {
        RuleEvaluationResult childResult = evaluate(notMatcher.child(), ruleEvaluationInput);
        MatcherEvaluationState status =
                switch (childResult.status()) {
                    case MATCHED -> MatcherEvaluationState.NOT_MATCHED;
                    case NOT_MATCHED -> MatcherEvaluationState.MATCHED;
                    case DEFERRED -> MatcherEvaluationState.DEFERRED;
                };
        return RuleEvaluationResult.compose(status, java.util.List.of(childResult));
    }

    private RuleEvaluationResult evaluateRecipient(
            String matcherNodeId,
            String expectedEmail,
            java.util.List<String> actualRecipients,
            String reason) {
        String normalizedExpectedEmail = normalized(expectedEmail);
        boolean matched =
                actualRecipients.stream()
                        .anyMatch(
                                actualRecipient ->
                                        normalized(actualRecipient)
                                                .equals(normalizedExpectedEmail));
        return terminal(matcherNodeId, matched, reason);
    }

    private RuleEvaluationResult evaluateTextEquals(
            String matcherNodeId, String expectedText, String actualText, String reason) {
        return terminal(
                matcherNodeId, normalized(actualText).equals(normalized(expectedText)), reason);
    }

    private static RuleEvaluationResult terminal(
            String matcherNodeId, boolean matched, String evidenceReason) {
        return matched
                ? RuleEvaluationResult.matched(matcherNodeId, evidenceReason)
                : RuleEvaluationResult.notMatched(matcherNodeId, evidenceReason);
    }

    private static String normalized(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
