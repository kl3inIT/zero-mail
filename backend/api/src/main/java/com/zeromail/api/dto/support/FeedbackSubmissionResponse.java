package com.zeromail.api.dto.support;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"id"})
public record FeedbackSubmissionResponse(UUID id) {}
