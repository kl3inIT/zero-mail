package com.zeromail.core.support.usecases;

import com.zeromail.core.support.domain.FeedbackType;
import java.util.UUID;

public record SubmitFeedbackCommand(
        UUID tenantId, FeedbackType type, String subject, String message, String contactEmail) {}
