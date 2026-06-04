package com.zeromail.api.controllers.cleanup;

import com.zeromail.api.dto.cleanup.CleanupSenderActionRequest;
import com.zeromail.api.dto.cleanup.CleanupSenderActionResponse;
import com.zeromail.core.cleanup.usecases.CleanupSenderActionService;
import com.zeromail.core.cleanup.usecases.CleanupSenderActionService.BulkSenderActionResult;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "cleanup")
@RequestMapping("/api/unsubscribe/senders")
public class CleanupSenderActionController {

    private static final Logger log = LoggerFactory.getLogger(CleanupSenderActionController.class);

    private final CleanupSenderActionService cleanupSenderActionService;

    public CleanupSenderActionController(CleanupSenderActionService cleanupSenderActionService) {
        this.cleanupSenderActionService = cleanupSenderActionService;
    }

    @PostMapping("/action")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CleanupSenderActionResponse action(
            @Valid @RequestBody CleanupSenderActionRequest request) {
        validateRequest(request);
        UUID tenantId = TenantContext.currentTenantUuid();
        BulkSenderActionResult result =
                switch (request.action()) {
                    case APPROVE ->
                            cleanupSenderActionService.approve(tenantId, request.senderEmails());
                    case UNAPPROVE ->
                            cleanupSenderActionService.unapprove(tenantId, request.senderEmails());
                    case MARK_UNSUBSCRIBED ->
                            cleanupSenderActionService.markUnsubscribed(
                                    tenantId, request.senderEmails());
                    case AUTO_ARCHIVE ->
                            cleanupSenderActionService.autoArchive(
                                    tenantId, request.senderEmails());
                    case ARCHIVE ->
                            cleanupSenderActionService.archiveHistory(
                                    tenantId, request.senderEmails());
                    case DELETE ->
                            cleanupSenderActionService.trashHistory(
                                    tenantId, request.senderEmails());
                    case LABEL_FUTURE ->
                            cleanupSenderActionService.labelFuture(
                                    tenantId, request.senderEmails(), request.labelName());
                };
        log.info(
                "event=cleanup_sender_action_requested tenantId={} action={} senderCount={}",
                tenantId,
                request.action(),
                result.senderCount());
        return CleanupSenderActionResponse.from(result);
    }

    static void validateRequest(CleanupSenderActionRequest request) {
        if (request.action() == CleanupSenderActionRequest.Action.LABEL_FUTURE
                && (request.labelName() == null || request.labelName().isBlank())) {
            throw new IllegalArgumentException("labelName is required for LABEL_FUTURE");
        }
    }
}
