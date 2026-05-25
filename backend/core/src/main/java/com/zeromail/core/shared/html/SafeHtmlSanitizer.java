package com.zeromail.core.shared.html;

import java.util.Map;

public interface SafeHtmlSanitizer {

    String sanitizeEmailHtml(String html);

    String replaceCidImageSources(String html, Map<String, String> imageDataUrisByContentId);
}
