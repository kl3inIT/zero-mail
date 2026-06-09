package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.feedback.FeedbackListResponse;
import com.zeromail.api.dto.admin.feedback.FeedbackResolveRequest;
import com.zeromail.core.support.domain.FeedbackStatus;
import com.zeromail.core.support.usecases.FeedbackListQuery;
import com.zeromail.core.support.usecases.FeedbackSubmissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-feedback")
@RequestMapping("/api/admin/feedback")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeedbackController {

    private final FeedbackSubmissionService feedbackSubmissionService;

    public AdminFeedbackController(FeedbackSubmissionService feedbackSubmissionService) {
        this.feedbackSubmissionService = feedbackSubmissionService;
    }

    @GetMapping
    public FeedbackListResponse list(
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(defaultValue = "50") int limit) {
        FeedbackListQuery.Result result =
                feedbackSubmissionService.list(new FeedbackListQuery.Filters(status, limit));
        return FeedbackListResponse.from(result);
    }

    @PatchMapping("/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) FeedbackResolveRequest request) {
        String adminNotes = request != null ? request.adminNotes() : null;
        feedbackSubmissionService.resolve(id, adminNotes);
    }

    @PatchMapping("/{id}/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopen(@PathVariable UUID id) {
        feedbackSubmissionService.reopen(id);
    }
}
