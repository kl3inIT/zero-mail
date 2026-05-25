package com.zeromail.core.llm.gateway.sanitization;

import com.zeromail.core.shared.html.SafeHtmlSanitizer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class JsoupSafeHtmlSanitizer implements SafeHtmlSanitizer {

    private static final Safelist EMAIL_HTML_SAFE_LIST =
            Safelist.relaxed()
                    .addTags("center", "div", "font", "span")
                    .addAttributes(
                            ":all", "align", "class", "dir", "height", "id", "lang", "style",
                            "title", "width")
                    .addAttributes(
                            "table",
                            "bgcolor",
                            "border",
                            "cellpadding",
                            "cellspacing",
                            "role",
                            "summary")
                    .addAttributes("td", "bgcolor", "colspan", "rowspan", "valign")
                    .addAttributes("th", "bgcolor", "colspan", "rowspan", "scope", "valign")
                    .addAttributes("img", "alt", "border", "height", "src", "width")
                    .addProtocols("a", "href", "http", "https", "mailto")
                    .addProtocols("img", "src", "http", "https", "data")
                    .preserveRelativeLinks(false);

    private static final Document.OutputSettings OUTPUT_SETTINGS =
            new Document.OutputSettings().prettyPrint(false);

    @Override
    public String sanitizeEmailHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, "", EMAIL_HTML_SAFE_LIST, OUTPUT_SETTINGS).trim();
    }

    @Override
    public String replaceCidImageSources(
            String html, Map<String, String> imageDataUrisByContentId) {
        if (html == null
                || html.isBlank()
                || imageDataUrisByContentId == null
                || imageDataUrisByContentId.isEmpty()) {
            return html == null ? "" : html;
        }
        Document document = Jsoup.parseBodyFragment(html);
        for (Element imageElement : document.select("img[src]")) {
            String source = imageElement.attr("src");
            if (!source.regionMatches(true, 0, "cid:", 0, 4)) {
                continue;
            }
            String normalizedContentId = normalizedContentId(source.substring(4));
            String dataUri = imageDataUrisByContentId.get(normalizedContentId);
            if (dataUri != null && !dataUri.isBlank()) {
                imageElement.attr("src", dataUri);
            }
        }
        return document.body().html();
    }

    private static String normalizedContentId(String rawContentId) {
        if (rawContentId == null || rawContentId.isBlank()) {
            return "";
        }
        String decodedContentId;
        try {
            decodedContentId = URLDecoder.decode(rawContentId, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidEncodedContentId) {
            decodedContentId = rawContentId;
        }
        return decodedContentId.replace("<", "").replace(">", "").trim().toLowerCase(Locale.ROOT);
    }
}
