package com.zeromail.api.dto.cleanup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CleanupSenderActionRequest(
        @NotNull Action action,
        @NotEmpty(message = "Sender list must not be empty")
                @Size(max = 25, message = "Sender list exceeds cap of 25")
                List<@NotBlank @Size(max = 320) String> senderEmails,
        @Size(max = 500) String labelName) {

    public enum Action {
        APPROVE,
        UNAPPROVE,
        MARK_UNSUBSCRIBED,
        AUTO_ARCHIVE,
        ARCHIVE,
        DELETE,
        LABEL_FUTURE
    }
}
