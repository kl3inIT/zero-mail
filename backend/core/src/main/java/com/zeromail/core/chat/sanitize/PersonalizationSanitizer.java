package com.zeromail.core.chat.sanitize;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PersonalizationSanitizer {

    private static final int LENGTH_CAP = 2000;
    private static final List<String> SENTINELS =
            List.of("[SYSTEM]", "[/SYSTEM]", "</s>", "### system", "<|im_start|>", "<|im_end|>");
    private static final Pattern MARKDOWN_HEADER = Pattern.compile("(?m)^#{1,6}\\s");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\t\\n]]");

    public String sanitize(String rawPersonalizationInput) {
        if (rawPersonalizationInput == null || rawPersonalizationInput.isBlank()) {
            return "";
        }

        String sanitizedText = rawPersonalizationInput;
        for (String sentinel : SENTINELS) {
            sanitizedText = sanitizedText.replace(sentinel, "");
        }
        sanitizedText = MARKDOWN_HEADER.matcher(sanitizedText).replaceAll("");
        sanitizedText = CONTROL_CHARS.matcher(sanitizedText).replaceAll("");
        sanitizedText = sanitizedText.trim();
        if (sanitizedText.length() > LENGTH_CAP) {
            sanitizedText = sanitizedText.substring(0, LENGTH_CAP);
        }
        return sanitizedText;
    }
}
