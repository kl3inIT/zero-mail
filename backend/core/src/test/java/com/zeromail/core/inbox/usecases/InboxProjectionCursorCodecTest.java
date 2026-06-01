package com.zeromail.core.inbox.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.shared.crypto.CryptoProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the keyset cursor codec. The key invariants are:
 *
 * <ol>
 *   <li>Round-trip preserves the {@code (receivedAt, gmailMessageId)} tuple losslessly down to
 *       microsecond precision — Postgres {@code timestamptz} stores microseconds, and the codec
 *       multiplies/divides by 1e6 to avoid Instant nanos round-trip loss.
 *   <li>Null / blank input maps to {@link InboxProjectionCursor#firstPage()} (the read service
 *       treats "no cursor" and "first page" identically).
 *   <li>Tampering with any byte invalidates the HMAC signature.
 * </ol>
 */
class InboxProjectionCursorCodecTest {

    private static final String ZERO_KEY_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final InboxProjectionCursorCodec codec =
            new InboxProjectionCursorCodec(
                    new CryptoProperties(ZERO_KEY_BASE64, ZERO_KEY_BASE64, ZERO_KEY_BASE64));

    @Test
    void decode_null_cursor_returns_first_page() {
        InboxProjectionCursor decoded = codec.decode(null);

        assertThat(decoded.isFirstPage()).isTrue();
        assertThat(decoded.receivedAt()).isNull();
        assertThat(decoded.gmailMessageId()).isNull();
    }

    @Test
    void decode_blank_cursor_returns_first_page() {
        assertThat(codec.decode("   ").isFirstPage()).isTrue();
        assertThat(codec.decode("").isFirstPage()).isTrue();
    }

    @Test
    void round_trip_preserves_received_at_at_microsecond_precision() {
        Instant receivedAt = Instant.parse("2026-06-01T03:04:05.678901Z");
        String encoded = codec.encode(receivedAt, "190000000000aa01");

        InboxProjectionCursor decoded = codec.decode(encoded);

        assertThat(decoded.gmailMessageId()).isEqualTo("190000000000aa01");
        assertThat(decoded.receivedAt()).isEqualTo(receivedAt);
    }

    @Test
    void round_trip_truncates_nanosecond_precision_to_microseconds() {
        Instant receivedAtWithNanos = Instant.parse("2026-06-01T03:04:05.678901999Z");
        String encoded = codec.encode(receivedAtWithNanos, "190000000000aa01");

        InboxProjectionCursor decoded = codec.decode(encoded);

        // Postgres timestamptz only stores microseconds; the codec must round-trip the
        // microsecond-precision version so the next-page query lines up with the stored row.
        assertThat(decoded.receivedAt()).isEqualTo(Instant.parse("2026-06-01T03:04:05.678901Z"));
    }

    @Test
    void encode_requires_non_blank_gmail_message_id() {
        Instant receivedAt = Instant.parse("2026-06-01T00:00:00Z");

        assertThatThrownBy(() -> codec.encode(receivedAt, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_rejects_non_base64url_input() {
        assertThatThrownBy(() -> codec.decode("@@@not-base64@@@"))
                .isInstanceOf(InvalidProjectionCursorException.class);
    }

    @Test
    void decode_rejects_truncated_envelope() {
        Instant receivedAt = Instant.parse("2026-06-01T03:04:05Z");
        String encoded = codec.encode(receivedAt, "190000000000aa01");
        String truncated = encoded.substring(0, encoded.length() / 2);

        assertThatThrownBy(() -> codec.decode(truncated))
                .isInstanceOf(InvalidProjectionCursorException.class);
    }

    @Test
    void decode_rejects_cursor_signed_with_a_different_key() {
        InboxProjectionCursorCodec otherCodec =
                new InboxProjectionCursorCodec(
                        new CryptoProperties(
                                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA=",
                                ZERO_KEY_BASE64,
                                ZERO_KEY_BASE64));
        String foreignCursor =
                otherCodec.encode(Instant.parse("2026-06-01T03:04:05Z"), "190000000000aa01");

        assertThatThrownBy(() -> codec.decode(foreignCursor))
                .isInstanceOf(InvalidProjectionCursorException.class);
    }
}
