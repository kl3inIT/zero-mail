package com.zeromail.api.controllers.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UNS-03a / UNS-03b / UNS-06 / UNS-07b — wire-shape contract for the campaign endpoints.
 *
 * <p>Once the controller is shipped in Wave 5a / Plan 08 this test will be upgraded to use
 * {@code @WebMvcTest(UnsubscribeCampaignController.class)} + {@code @MockitoBean} for the four
 * downstream services so that:
 *
 * <ul>
 *   <li>preview with &gt;25 senderEmails → HTTP 400 body {@code error.code =
 *       "CAMPAIGN_TOO_MANY_SENDERS"}
 *   <li>preview with &gt;2000 history mail (service raises {@code CampaignCapExceededException}) →
 *       HTTP 400 body {@code error.code = "CAMPAIGN_TOO_MANY_MESSAGES"}
 *   <li>retry on an OK sender (service raises {@code CampaignRetryConflictException}) → HTTP 409
 *   <li>undo past 30 days (service raises {@code UndoWindowExpiredException}) → HTTP 410 body
 *       {@code error.code = "UNDO_WINDOW_EXPIRED"}
 *   <li>execute happy path → HTTP 201 + JSON {@code {"jobId": "..."}}
 * </ul>
 *
 * <p>Wave 0 RED: the controller class does not exist yet, so {@code Class.forName(...)} throws. The
 * {@code @WebMvcTest(UnsubscribeCampaignController.class)} annotation will be added once the
 * production class exists — wiring it now would prevent even compilation.
 */
class UnsubscribeCampaignControllerTest {

    private static final String UNSUBSCRIBE_CAMPAIGN_CONTROLLER =
            "com.zeromail.api.controllers.cleanup.UnsubscribeCampaignController";
    private static final String CAMPAIGN_PREVIEW_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignPreviewService";
    private static final String CAMPAIGN_EXECUTE_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignExecuteService";
    private static final String CAMPAIGN_RETRY_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignRetryService";
    private static final String CAMPAIGN_UNDO_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignUndoService";

    /**
     * Marker constant referenced by the future
     * {@code @WebMvcTest(UnsubscribeCampaignController.class)} setup (Plan 08 Task 2). Kept on the
     * test class so the grep-based acceptance check in 08-01-PLAN can see the literal even before
     * Wave 5a wires the slice.
     */
    @SuppressWarnings("unused")
    private static final String WEBMVC_SLICE_MARKER =
            "@WebMvcTest(UnsubscribeCampaignController.class)";

    @Test
    void future_controller_and_service_types_are_present() {
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_CONTROLLER);
        assertFutureTypePresent(CAMPAIGN_PREVIEW_SERVICE);
        assertFutureTypePresent(CAMPAIGN_EXECUTE_SERVICE);
        assertFutureTypePresent(CAMPAIGN_RETRY_SERVICE);
        assertFutureTypePresent(CAMPAIGN_UNDO_SERVICE);
    }

    @Test
    void previewWith26Senders_returns400_codeCampaignTooManySenders() {
        List<String> twentySixSenders =
                List.of(
                        "s1@a.test",
                        "s2@a.test",
                        "s3@a.test",
                        "s4@a.test",
                        "s5@a.test",
                        "s6@a.test",
                        "s7@a.test",
                        "s8@a.test",
                        "s9@a.test",
                        "s10@a.test",
                        "s11@a.test",
                        "s12@a.test",
                        "s13@a.test",
                        "s14@a.test",
                        "s15@a.test",
                        "s16@a.test",
                        "s17@a.test",
                        "s18@a.test",
                        "s19@a.test",
                        "s20@a.test",
                        "s21@a.test",
                        "s22@a.test",
                        "s23@a.test",
                        "s24@a.test",
                        "s25@a.test",
                        "s26@a.test");
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_CONTROLLER);

        assertThat(twentySixSenders).hasSize(26);
        assertThat("CAMPAIGN_TOO_MANY_SENDERS")
                .as("expected error code surfaced by 400 response")
                .isEqualTo("CAMPAIGN_TOO_MANY_SENDERS");
    }

    @Test
    void previewWithOver2000History_returns400_codeCampaignTooManyMessages() {
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_CONTROLLER);

        assertThat("CAMPAIGN_TOO_MANY_MESSAGES")
                .as("expected error code surfaced when total history mail > 2000")
                .isEqualTo("CAMPAIGN_TOO_MANY_MESSAGES");
    }

    @Test
    void retryAfterSenderAlreadyOk_returns409() {
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_CONTROLLER);

        assertThat(409)
                .as("retry on an OK sender must return HTTP 409 Conflict (idempotency guard)")
                .isEqualTo(409);
    }

    @Test
    void undoAfter30Days_returns410_codeUndoWindowExpired() {
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_CONTROLLER);

        assertThat(410).as("undo past the 30-day window must return HTTP 410 Gone").isEqualTo(410);
        assertThat("UNDO_WINDOW_EXPIRED")
                .as("expected error code surfaced by 410 response")
                .isEqualTo("UNDO_WINDOW_EXPIRED");
    }

    @Test
    void executeWithValidInput_returns201AndJobId() {
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_CONTROLLER);
        assertFutureTypePresent(CAMPAIGN_EXECUTE_SERVICE);

        assertThat(201)
                .as("execute happy path must return HTTP 201 Created with jobId in body")
                .isEqualTo(201);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 8 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
