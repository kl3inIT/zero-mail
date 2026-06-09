package com.zeromail.core.chat.usecases.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.chat.confirm.ConfirmationStateMachine.Reservation;
import com.zeromail.core.chat.confirm.send.AssistantSendCommand;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.domain.ToolCategory;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Regression: confirming a sendEmail whose subject is missing or cleared on the preview card must
 * NOT throw. Before the fix, {@code WriteToolArguments.text(..., "subject")} threw and {@link
 * com.zeromail.core.chat.usecases.ConfirmActionService} reverted the reservation to PENDING, so the
 * user could never send. Subject now defaults to empty (Gmail shows "(no subject)").
 */
class ConfirmedSendToolHandlersBlankSubjectTest {

    private final SenderSafetyNetService senderSafetyNetService =
            mock(SenderSafetyNetService.class);
    private final ConfirmedSendToolHandlers confirmedSendToolHandlers =
            new ConfirmedSendToolHandlers(senderSafetyNetService);

    @Test
    void missing_subject_defaults_to_empty_instead_of_throwing() {
        when(senderSafetyNetService.isProtected(any(UUID.class), anyString())).thenReturn(false);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("to", "recipient@acme.test");
        input.put("body", "Body the user reviewed.");

        AssistantSendCommand command =
                confirmedSendToolHandlers.toCommand(
                        reservation(input), false, null, "confirm-test-missing-subject");

        assertThat(command.subject()).isEmpty();
        assertThat(command.to()).isEqualTo("recipient@acme.test");
    }

    @Test
    void blank_subject_override_clears_subject_without_throwing() {
        when(senderSafetyNetService.isProtected(any(UUID.class), anyString())).thenReturn(false);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("to", "recipient@acme.test");
        input.put("subject", "Original subject");
        input.put("body", "Body the user reviewed.");

        assertThatCode(
                        () -> {
                            AssistantSendCommand command =
                                    confirmedSendToolHandlers.toCommand(
                                            reservation(input),
                                            false,
                                            Map.of("subject", "   "),
                                            "confirm-test-cleared-subject");
                            assertThat(command.subject()).isEmpty();
                        })
                .doesNotThrowAnyException();
    }

    private static Reservation reservation(Map<String, Object> input) {
        return new Reservation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tool-send-blank-subject",
                ChatToolName.SEND_EMAIL,
                ToolCategory.CONFIRMED_SEND,
                Map.copyOf(input),
                Map.of("state", "preview"));
    }
}
