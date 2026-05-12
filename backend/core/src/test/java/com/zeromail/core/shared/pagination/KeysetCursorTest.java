package com.zeromail.core.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KeysetCursorTest {

    @Test
    void uuid_and_string_cursors_round_trip_full_instant_precision() {
        Instant timestamp = Instant.ofEpochSecond(1_777_777_777L, 123_456_789);
        UUID auditId = UUID.fromString("00000000-0000-0000-0000-0000000005b4");

        KeysetCursor uuidCursor =
                KeysetCursor.decode(KeysetCursor.encode(timestamp, auditId)).orElseThrow();
        KeysetCursor stringCursor =
                KeysetCursor.decode(KeysetCursor.encode(timestamp, "thread:id:with:colon"))
                        .orElseThrow();

        assertThat(uuidCursor.timestamp()).isEqualTo(timestamp);
        assertThat(uuidCursor.id()).isEqualTo(auditId.toString());
        assertThat(uuidCursor.isNullsLast()).isFalse();
        assertThat(stringCursor.timestamp()).isEqualTo(timestamp);
        assertThat(stringCursor.id()).isEqualTo("thread:id:with:colon");
        assertThat(stringCursor.isNullsLast()).isFalse();
    }

    @Test
    void nulls_last_cursor_round_trips_without_timestamp() {
        KeysetCursor cursor = KeysetCursor.decode(KeysetCursor.nullsLast("thread-z")).orElseThrow();

        assertThat(cursor.timestamp()).isNull();
        assertThat(cursor.id()).isEqualTo("thread-z");
        assertThat(cursor.isNullsLast()).isTrue();
    }

    @Test
    void blank_cursor_decodes_to_empty_and_malformed_cursor_fails_loud() {
        assertThat(KeysetCursor.decode(null)).isEqualTo(Optional.empty());
        assertThat(KeysetCursor.decode("   ")).isEqualTo(Optional.empty());
        assertThatThrownBy(() -> KeysetCursor.decode("not-base64!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                KeysetCursor.decode(
                                        KeysetCursor.encode(Instant.EPOCH, "id").substring(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
