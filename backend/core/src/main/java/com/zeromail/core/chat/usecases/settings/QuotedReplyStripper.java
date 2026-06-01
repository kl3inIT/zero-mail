package com.zeromail.core.chat.usecases.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class QuotedReplyStripper {

    private static final List<Pattern> QUOTE_SEPARATORS =
            List.of(
                    Pattern.compile("^On .* wrote:$"),
                    Pattern.compile("^From: .*$"),
                    Pattern.compile("^-----Original Message-----"),
                    Pattern.compile("^________________________________"),
                    Pattern.compile("^Vào .* đã viết:$"),
                    Pattern.compile("^Người gửi: .*$"));

    private QuotedReplyStripper() {}

    public static String strip(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String[] lines = body.split("\\R", -1);
        List<String> keptLines = new ArrayList<>(lines.length);
        boolean changed = false;
        for (String line : lines) {
            if (isQuoteSeparator(line)) {
                changed = true;
                break;
            }
            if (line.startsWith(">")) {
                changed = true;
                continue;
            }
            keptLines.add(line);
        }
        if (!changed) {
            return body;
        }
        return String.join("\n", keptLines).stripTrailing();
    }

    private static boolean isQuoteSeparator(String line) {
        return QUOTE_SEPARATORS.stream().anyMatch(pattern -> pattern.matcher(line).matches());
    }
}
