package com.zeromail.api.controllers.support;

import com.zeromail.api.dto.support.FeedbackSubmissionRequest;
import com.zeromail.api.dto.support.FeedbackSubmissionResponse;
import com.zeromail.core.support.usecases.FeedbackSubmissionService;
import com.zeromail.core.support.usecases.SubmitFeedbackCommand;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "support")
@RequestMapping("/api/support")
public class FeedbackController {

    private final FeedbackSubmissionService feedbackSubmissionService;

    public FeedbackController(FeedbackSubmissionService feedbackSubmissionService) {
        this.feedbackSubmissionService = feedbackSubmissionService;
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackSubmissionResponse submitFeedback(
            @Valid @RequestBody FeedbackSubmissionRequest request) {
        UUID tenantId = tenantIdOrNull();
        SubmitFeedbackCommand command =
                new SubmitFeedbackCommand(
                        tenantId,
                        request.type(),
                        request.subject(),
                        request.message(),
                        request.contactEmail());
        UUID submissionId = feedbackSubmissionService.submit(command);
        return new FeedbackSubmissionResponse(submissionId);
    }

    private UUID tenantIdOrNull() {
        try {
            return TenantContext.currentTenantUuid();
        } catch (Exception ignored) {
            return null;
        }
    }
}
