package com.zeromail.core.aiEval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.services.gmail.model.Message;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.exception.MissingMessageIdException;
import com.zeromail.core.triage.usecases.ReplyMimeBuilder;
import com.zeromail.core.triage.usecases.ThreadingHeaderValidator;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class DraftThreadingEvalTest {

    private static final String THREAD_ID = "synthetic-thread-alpha";
    private static final String BODY = "Synthetic draft body for the eval fixture.";

    @Test
    void dim6_reply_mime_headers_cover_threading_edge_cases() throws Exception {
        List<ThreadingFixture> fixtures =
                List.of(
                        new ThreadingFixture(
                                "normal-chain",
                                "<alpha-parent@synthetic.test>",
                                "<root@synthetic.test> <previous@synthetic.test>",
                                "Quarterly planning",
                                "founder@synthetic.test",
                                "Re: Quarterly planning",
                                "<root@synthetic.test> <previous@synthetic.test> <alpha-parent@synthetic.test>"),
                        new ThreadingFixture(
                                "no-prior-references",
                                "<beta-parent@synthetic.test>",
                                null,
                                "Budget question",
                                "finance@synthetic.test",
                                "Re: Budget question",
                                "<beta-parent@synthetic.test>"),
                        new ThreadingFixture(
                                "already-prefixed",
                                "<gamma-parent@synthetic.test>",
                                "<gamma-root@synthetic.test>",
                                "Re: Vendor contract",
                                "ops@synthetic.test",
                                "Re: Vendor contract",
                                "<gamma-root@synthetic.test> <gamma-parent@synthetic.test>"),
                        new ThreadingFixture(
                                "vietnamese-subject",
                                "<delta-parent@synthetic.test>",
                                null,
                                "Lịch họp tuần này",
                                "partner@synthetic.test",
                                "Re: Lịch họp tuần này",
                                "<delta-parent@synthetic.test>"),
                        new ThreadingFixture(
                                "overlapping-participant-a",
                                "<epsilon-parent@synthetic.test>",
                                "<shared-root@synthetic.test>",
                                "Partner launch",
                                "shared-contact@synthetic.test",
                                "Re: Partner launch",
                                "<shared-root@synthetic.test> <epsilon-parent@synthetic.test>"),
                        new ThreadingFixture(
                                "overlapping-participant-b",
                                "<zeta-parent@synthetic.test>",
                                "<other-root@synthetic.test>",
                                "Investor update",
                                "shared-contact@synthetic.test",
                                "Re: Investor update",
                                "<other-root@synthetic.test> <zeta-parent@synthetic.test>"));

        for (ThreadingFixture fixture : fixtures) {
            String rawMime =
                    ReplyMimeBuilder.buildBase64UrlMime(
                            fixture.replyHeaders(), BODY + " " + fixture.id());
            MimeMessage parsedMime = ReplyMimeBuilder.parseBase64UrlMime(rawMime);
            Message gmailMessage = new Message().setThreadId(THREAD_ID).setRaw(rawMime);

            ThreadingHeaderValidator.validate(parsedMime, gmailMessage, THREAD_ID);

            assertThat(rawMime).doesNotContain("=");
            assertThat(rawMime).matches("[A-Za-z0-9_-]+");
            assertThat(parsedMime.getHeader("In-Reply-To", null))
                    .isEqualTo(fixture.inboundMessageId());
            assertThat(parsedMime.getHeader("References", null))
                    .isEqualTo(fixture.expectedReferences());
            assertThat(parsedMime.getSubject()).isEqualTo(fixture.expectedSubject());
            assertThat(parsedMime.getRecipients(jakarta.mail.Message.RecipientType.TO))
                    .extracting(Object::toString)
                    .containsExactly(fixture.replyToAddress());
        }
    }

    @Test
    void dim6_missing_message_id_fails_closed_before_gmail_create() {
        ReplyHeaders missingMessageId =
                ReplyHeaders.of(null, null, "Missing id", "sender@synthetic.test", THREAD_ID);

        assertThatThrownBy(() -> ReplyMimeBuilder.buildBase64UrlMime(missingMessageId, BODY))
                .isInstanceOf(MissingMessageIdException.class)
                .hasMessage(null);
    }

    @Test
    void dim6_cross_thread_pair_does_not_bleed_other_fixture_content() throws Exception {
        String threadASecret = "THREAD_A_SYNTHETIC_SENTINEL";
        String threadBSecret = "THREAD_B_SYNTHETIC_SENTINEL";

        String threadAMime =
                ReplyMimeBuilder.buildBase64UrlMime(
                        ReplyHeaders.of(
                                "<thread-a@synthetic.test>",
                                null,
                                "Thread A",
                                "shared-contact@synthetic.test",
                                "thread-a"),
                        "Only " + threadASecret + " belongs here.");
        String decodedThreadAMime =
                new String(Base64.getUrlDecoder().decode(threadAMime), StandardCharsets.UTF_8);

        assertThat(decodedThreadAMime).contains(threadASecret).doesNotContain(threadBSecret);
    }

    private record ThreadingFixture(
            String id,
            String inboundMessageId,
            String priorReferences,
            String inboundSubject,
            String replyToAddress,
            String expectedSubject,
            String expectedReferences) {

        private ReplyHeaders replyHeaders() {
            return ReplyHeaders.of(
                    inboundMessageId, priorReferences, inboundSubject, replyToAddress, THREAD_ID);
        }
    }
}
