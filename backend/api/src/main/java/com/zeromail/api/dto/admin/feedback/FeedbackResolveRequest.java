package com.zeromail.api.dto.admin.feedback;

import jakarta.validation.constraints.Size;

public record FeedbackResolveRequest(@Size(max = 2000) String adminNotes) {}
