package com.zeromail.api.dto.support;

import com.zeromail.core.support.domain.FeedbackType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"type", "subject", "message", "contactEmail"})
public record FeedbackSubmissionRequest(
        @NotNull FeedbackType type,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 5000) String message,
        @NotBlank @Email @Size(max = 320) String contactEmail) {}
