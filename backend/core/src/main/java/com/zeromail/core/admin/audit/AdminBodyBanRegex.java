package com.zeromail.core.admin.audit;

import java.util.regex.Pattern;

public final class AdminBodyBanRegex {

    public static final Pattern FORBIDDEN_FIELD_NAME =
            Pattern.compile("(?i).*(body|bodyHtml|snippet|payload|prompt|completion|content).*");

    private AdminBodyBanRegex() {}
}
