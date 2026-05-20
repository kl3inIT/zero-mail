package com.zeromail.api.dto.cleanup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code POST /api/unsubscribe/campaigns/preview} (UNS-03).
 *
 * <p>The wire-level {@code @Size(max = 25)} mirrors {@link
 * com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy#MAX_SENDERS_PER_CAMPAIGN}. Per-item
 * {@code @Size(max = 320)} guards against pathological inputs ahead of the service cap check.
 */
public record CampaignPreviewRequest(
        @NotEmpty(message = "Sender list must not be empty") @Size(max = 25, message = "Sender list exceeds cap of 25") List<@NotBlank @Size(max = 320) String> senderEmails) {}
