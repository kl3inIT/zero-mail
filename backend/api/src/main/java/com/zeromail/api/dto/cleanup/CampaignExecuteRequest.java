package com.zeromail.api.dto.cleanup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code POST /api/unsubscribe/campaigns/execute} (UNS-04). Shape matches {@link
 * CampaignPreviewRequest} — execute is preview's commit step.
 */
public record CampaignExecuteRequest(
        @NotEmpty(message = "Sender list must not be empty") @Size(max = 25, message = "Sender list exceeds cap of 25") List<@NotBlank @Size(max = 320) String> senderEmails) {}
