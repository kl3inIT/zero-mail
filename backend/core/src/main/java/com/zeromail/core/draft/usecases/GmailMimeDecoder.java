package com.zeromail.core.draft.usecases;

import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * URL-safe Base64 decoding helpers shared by Gmail mail-content walkers in the draft pipeline. Both
 * {@code DraftReplySourceLoader} and {@code ToneContextBuilder} need to extract readable text from
 * {@link MessagePart} payloads; the decoding rules (URL-safe alphabet, no padding,
 * empty-on-malformed) are intentionally lenient and live here to keep the two walkers in lockstep.
 */
final class GmailMimeDecoder {

    private GmailMimeDecoder() {}

    static String decodedBody(MessagePart payload) {
        MessagePartBody body = payload.getBody();
        if (body == null || body.getData() == null || body.getData().isBlank()) {
            return "";
        }
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(body.getData());
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidBase64) {
            return "";
        }
    }

    static boolean isReadableMimeType(String mimeType) {
        return mimeType == null
                || mimeType.equalsIgnoreCase("text/plain")
                || mimeType.equalsIgnoreCase("text/html");
    }
}
