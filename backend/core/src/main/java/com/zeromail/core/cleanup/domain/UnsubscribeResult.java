package com.zeromail.core.cleanup.domain;

import java.util.Objects;

/**
 * Result of an unsubscribe attempt — uniform return type for both {@link
 * com.zeromail.core.cleanup.usecases.UnsubscribeHttpClient#postOneClick} (HTTP one-click POST per
 * RFC 8058) and {@link
 * com.zeromail.core.cleanup.usecases.UnsubscribeMailtoSender#sendUnsubscribeMailto} (Gmail
 * send-as-self mailto per RFC 6068).
 *
 * <p>Sealed interface with two record permittees per D-05 + D-07 + D-08:
 *
 * <ul>
 *   <li>{@link Ok} — success. {@code identifier} is the HTTP status code (e.g. "200") for HTTP
 *       one-click or the Gmail messageId of the sent mailto message.
 *   <li>{@link Failed} — failure. {@code failureReason} is a stable token consumed by the campaign
 *       orchestrator (Wave 4b) for persistence and UI surfacing. Canonical reasons: {@code
 *       HTTP_3XX_REDIRECT}, {@code HTTP_4XX_<code>}, {@code HTTP_5XX_<code>}, {@code TIMEOUT},
 *       {@code NETWORK_ERROR}, {@code MAILTO_RECIPIENT_MISMATCH}, {@code MAILTO_INVALID_URI},
 *       {@code MIME_BUILD_ERROR}, {@code GMAIL_HTTP_<code>}.
 * </ul>
 *
 * <p>Lives in {@code core.cleanup.domain} per Phase 8 Wave 0 contract — business vocabulary, not a
 * use-case service. Referenced by Wave 0 test {@code
 * com.zeromail.core.cleanup.http.UnsubscribeHttpClientTest}.
 */
public sealed interface UnsubscribeResult permits UnsubscribeResult.Ok, UnsubscribeResult.Failed {

    /** Success variant. {@code identifier} = HTTP statusCode string OR Gmail messageId. */
    record Ok(String identifier) implements UnsubscribeResult {
        public Ok {
            Objects.requireNonNull(identifier, "identifier must not be null");
            if (identifier.isBlank()) {
                throw new IllegalArgumentException("identifier must not be blank");
            }
        }
    }

    /** Failure variant. {@code failureReason} is a stable token (see interface javadoc). */
    record Failed(String failureReason) implements UnsubscribeResult {
        public Failed {
            Objects.requireNonNull(failureReason, "failureReason must not be null");
            if (failureReason.isBlank()) {
                throw new IllegalArgumentException("failureReason must not be blank");
            }
        }
    }

    static UnsubscribeResult ok(String identifier) {
        return new Ok(identifier);
    }

    static UnsubscribeResult failed(String failureReason) {
        return new Failed(failureReason);
    }

    default boolean isOk() {
        return this instanceof Ok;
    }
}
