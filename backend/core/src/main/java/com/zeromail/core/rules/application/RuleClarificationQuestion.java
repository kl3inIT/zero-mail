package com.zeromail.core.rules.application;

import com.zeromail.core.rules.domain.RuleLanguage;

public record RuleClarificationQuestion(RuleLanguage language, String question) {

    public static final int MAX_QUESTION_LENGTH = 240;

    public RuleClarificationQuestion {
        if (language == null) {
            throw new IllegalArgumentException("language must not be null");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        question = question.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("question is too long");
        }
    }
}
