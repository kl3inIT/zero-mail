package com.zeromail.core.chat.usecases.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QuotedReplyStripperTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void stripsQuotedReplyContent(String name, String input, String expected) {
        assertThat(QuotedReplyStripper.strip(input)).isEqualTo(expected);
    }

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("quote prefix", "Thanks\n> old inbound\n> more", "Thanks"),
                Arguments.of("gmail separator", "Thanks\nOn Tue, Founder wrote:\nold", "Thanks"),
                Arguments.of("from separator", "Thanks\nFrom: sender@example.test\nold", "Thanks"),
                Arguments.of(
                        "outlook separator",
                        "Thanks\n________________________________\nold",
                        "Thanks"),
                Arguments.of("vietnamese wrote", "Cảm ơn\nVào thứ ba đã viết:\ncũ", "Cảm ơn"),
                Arguments.of("vietnamese sender", "Cảm ơn\nNgười gửi: A\ncũ", "Cảm ơn"),
                Arguments.of("no quote", "Plain outbound text", "Plain outbound text"),
                Arguments.of("separator only", "On Tue, Founder wrote:", ""),
                Arguments.of(
                        "quoted sentinel",
                        "My reply\nOn Tue, Founder wrote:\nLEAK_SENTINEL_QUOTED",
                        "My reply"));
    }
}
