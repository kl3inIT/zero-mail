package com.zeromail.api.dto.composer;

import com.zeromail.core.composer.domain.ComposerMode;
import com.zeromail.core.composer.usecases.ComposerDraftUpsertCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/composer/drafts}.
 *
 * <p>{@code mode} is one of {@code reply | reply_all | forward}. {@code rfc822MessageId} is the RFC
 * 822 {@code Message-ID} (with angle brackets) of the email being replied to or forwarded; for
 * forward mode it may be omitted because Gmail doesn't expect {@code In-Reply-To} on forwards.
 *
 * <p>Body and recipients are user-authored draft data (CLAUDE.md draft-body carve-out).
 */
@Schema(allowableValues = {"reply", "reply_all", "forward"})
public record ComposerDraftUpsertRequestDto(
        @NotBlank @Size(max = 256) String gmailThreadId,
        @Size(max = 256) String sourceGmailMessageId,
        @Size(max = 998) String rfc822MessageId,
        @Size(max = 16384) String priorReferences,
        @NotBlank @Schema(allowableValues = {"reply", "reply_all", "forward"}) String mode,
        @Size(max = 4096) String toAddresses,
        @Size(max = 4096) String ccAddresses,
        @Size(max = 4096) String bccAddresses,
        @Size(max = 998) String subject,
        @Size(max = 32768) String body) {

    public ComposerDraftUpsertCommand toCommand() {
        return new ComposerDraftUpsertCommand(
                gmailThreadId,
                sourceGmailMessageId,
                rfc822MessageId,
                priorReferences,
                ComposerMode.fromId(mode),
                toAddresses,
                ccAddresses,
                bccAddresses,
                subject,
                body);
    }
}
