package com.zeromail.core.rules.domain;

import com.google.re2j.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public sealed interface MatcherNode
        permits MatcherNode.SenderEmailMatcher,
                MatcherNode.SenderDomainMatcher,
                MatcherNode.RecipientToMatcher,
                MatcherNode.RecipientCcMatcher,
                MatcherNode.SubjectContainsMatcher,
                MatcherNode.SubjectEqualsMatcher,
                MatcherNode.SubjectRegexMatcher,
                MatcherNode.GmailLabelPresentMatcher,
                MatcherNode.GmailLabelAbsentMatcher,
                MatcherNode.GmailCategoryPresentMatcher,
                MatcherNode.GmailCategoryAbsentMatcher,
                MatcherNode.HasAttachmentMatcher,
                MatcherNode.ListUnsubscribePresentMatcher,
                MatcherNode.NewsletterIndicatorMatcher,
                MatcherNode.MessageAgeMatcher,
                MatcherNode.MessageDateMatcher,
                MatcherNode.AllMatcher,
                MatcherNode.AnyMatcher,
                MatcherNode.NotMatcher,
                SemanticIntentMatcher {

    String nodeId();

    MatcherType type();

    boolean requiresBodyEvidence();

    record SenderEmailMatcher(String nodeId, String email) implements MatcherNode {
        public SenderEmailMatcher {
            requireText(nodeId, "nodeId");
            requireText(email, "email");
        }

        @Override
        public MatcherType type() {
            return MatcherType.SENDER_EMAIL;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record SenderDomainMatcher(String nodeId, String domain) implements MatcherNode {
        public SenderDomainMatcher {
            requireText(nodeId, "nodeId");
            requireText(domain, "domain");
        }

        @Override
        public MatcherType type() {
            return MatcherType.SENDER_DOMAIN;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record RecipientToMatcher(String nodeId, String email) implements MatcherNode {
        public RecipientToMatcher {
            requireText(nodeId, "nodeId");
            requireText(email, "email");
        }

        @Override
        public MatcherType type() {
            return MatcherType.RECIPIENT_TO;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record RecipientCcMatcher(String nodeId, String email) implements MatcherNode {
        public RecipientCcMatcher {
            requireText(nodeId, "nodeId");
            requireText(email, "email");
        }

        @Override
        public MatcherType type() {
            return MatcherType.RECIPIENT_CC;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record SubjectContainsMatcher(String nodeId, String text) implements MatcherNode {
        public SubjectContainsMatcher {
            requireText(nodeId, "nodeId");
            requireText(text, "text");
        }

        @Override
        public MatcherType type() {
            return MatcherType.SUBJECT_CONTAINS;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record SubjectEqualsMatcher(String nodeId, String text) implements MatcherNode {
        public SubjectEqualsMatcher {
            requireText(nodeId, "nodeId");
            requireText(text, "text");
        }

        @Override
        public MatcherType type() {
            return MatcherType.SUBJECT_EQUALS;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record SubjectRegexMatcher(String nodeId, String regexPattern) implements MatcherNode {
        public SubjectRegexMatcher {
            requireText(nodeId, "nodeId");
            requireText(regexPattern, "regexPattern");
            try {
                Pattern.compile(regexPattern);
            } catch (RuntimeException invalidRegexException) {
                throw new IllegalArgumentException(
                        "Invalid RE2J subject regex", invalidRegexException);
            }
        }

        @Override
        public MatcherType type() {
            return MatcherType.SUBJECT_REGEX;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record GmailLabelPresentMatcher(String nodeId, String labelId) implements MatcherNode {
        public GmailLabelPresentMatcher {
            requireText(nodeId, "nodeId");
            requireText(labelId, "labelId");
        }

        @Override
        public MatcherType type() {
            return MatcherType.GMAIL_LABEL_PRESENT;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record GmailLabelAbsentMatcher(String nodeId, String labelId) implements MatcherNode {
        public GmailLabelAbsentMatcher {
            requireText(nodeId, "nodeId");
            requireText(labelId, "labelId");
        }

        @Override
        public MatcherType type() {
            return MatcherType.GMAIL_LABEL_ABSENT;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record GmailCategoryPresentMatcher(String nodeId, String category) implements MatcherNode {
        public GmailCategoryPresentMatcher {
            requireText(nodeId, "nodeId");
            requireText(category, "category");
        }

        @Override
        public MatcherType type() {
            return MatcherType.GMAIL_CATEGORY_PRESENT;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record GmailCategoryAbsentMatcher(String nodeId, String category) implements MatcherNode {
        public GmailCategoryAbsentMatcher {
            requireText(nodeId, "nodeId");
            requireText(category, "category");
        }

        @Override
        public MatcherType type() {
            return MatcherType.GMAIL_CATEGORY_ABSENT;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record HasAttachmentMatcher(String nodeId) implements MatcherNode {
        public HasAttachmentMatcher {
            requireText(nodeId, "nodeId");
        }

        @Override
        public MatcherType type() {
            return MatcherType.HAS_ATTACHMENT;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record ListUnsubscribePresentMatcher(String nodeId) implements MatcherNode {
        public ListUnsubscribePresentMatcher {
            requireText(nodeId, "nodeId");
        }

        @Override
        public MatcherType type() {
            return MatcherType.LIST_UNSUBSCRIBE_PRESENT;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record NewsletterIndicatorMatcher(String nodeId) implements MatcherNode {
        public NewsletterIndicatorMatcher {
            requireText(nodeId, "nodeId");
        }

        @Override
        public MatcherType type() {
            return MatcherType.NEWSLETTER_INDICATOR;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record MessageAgeMatcher(String nodeId, MessageAgeOperator operator, int days)
            implements MatcherNode {
        public MessageAgeMatcher {
            requireText(nodeId, "nodeId");
            Objects.requireNonNull(operator, "operator must not be null");
            if (days < 0) {
                throw new IllegalArgumentException("days must not be negative");
            }
        }

        @Override
        public MatcherType type() {
            return MatcherType.MESSAGE_AGE;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record MessageDateMatcher(String nodeId, MessageDateOperator operator, LocalDate date)
            implements MatcherNode {
        public MessageDateMatcher {
            requireText(nodeId, "nodeId");
            Objects.requireNonNull(operator, "operator must not be null");
            Objects.requireNonNull(date, "date must not be null");
        }

        @Override
        public MatcherType type() {
            return MatcherType.MESSAGE_DATE;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return false;
        }
    }

    record AllMatcher(String nodeId, List<MatcherNode> children) implements MatcherNode {
        public AllMatcher {
            requireText(nodeId, "nodeId");
            children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
            if (children.isEmpty()) {
                throw new IllegalArgumentException("children must not be empty");
            }
        }

        @Override
        public MatcherType type() {
            return MatcherType.ALL;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return children.stream().anyMatch(MatcherNode::requiresBodyEvidence);
        }
    }

    record AnyMatcher(String nodeId, List<MatcherNode> children) implements MatcherNode {
        public AnyMatcher {
            requireText(nodeId, "nodeId");
            children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
            if (children.isEmpty()) {
                throw new IllegalArgumentException("children must not be empty");
            }
        }

        @Override
        public MatcherType type() {
            return MatcherType.ANY;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return children.stream().anyMatch(MatcherNode::requiresBodyEvidence);
        }
    }

    record NotMatcher(String nodeId, MatcherNode child) implements MatcherNode {
        public NotMatcher {
            requireText(nodeId, "nodeId");
            Objects.requireNonNull(child, "child must not be null");
        }

        @Override
        public MatcherType type() {
            return MatcherType.NOT;
        }

        @Override
        public boolean requiresBodyEvidence() {
            return child.requiresBodyEvidence();
        }
    }

    enum MessageAgeOperator {
        OLDER_THAN_DAYS,
        NEWER_THAN_DAYS
    }

    enum MessageDateOperator {
        BEFORE,
        ON,
        AFTER
    }

    private static void requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
