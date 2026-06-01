package com.zeromail.core.chat.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class KnowledgeSnippetException extends BusinessException {

    private final ErrorClass errorClass;
    private final String errorCode;
    private final String logEvent;
    private final String title;
    private final String detail;

    private KnowledgeSnippetException(
            ErrorClass errorClass, String errorCode, String logEvent, String title, String detail) {
        super(errorCode);
        this.errorClass = errorClass;
        this.errorCode = errorCode;
        this.logEvent = logEvent;
        this.title = title;
        this.detail = detail;
    }

    public static KnowledgeSnippetException duplicateTitle() {
        return new KnowledgeSnippetException(
                ErrorClass.CONFLICT,
                ErrorCodes.KNOWLEDGE_TITLE_DUPLICATE,
                "knowledge_title_duplicate",
                "Duplicate knowledge title",
                "A knowledge snippet with that title already exists.");
    }

    public static KnowledgeSnippetException notFound() {
        return new KnowledgeSnippetException(
                ErrorClass.NOT_FOUND,
                ErrorCodes.KNOWLEDGE_NOT_FOUND,
                "knowledge_snippet_not_found",
                "Knowledge snippet not found",
                "The requested knowledge snippet was not found.");
    }

    @Override
    public ErrorClass errorClass() {
        return errorClass;
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    @Override
    public String logEvent() {
        return logEvent;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String detail() {
        return detail;
    }
}
