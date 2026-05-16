package com.zeromail.api.controllers.thread;

import com.zeromail.api.dto.thread.ThreadDraftResponse;
import com.zeromail.core.draft.usecases.GenerateThreadDraftCommand;
import com.zeromail.core.draft.usecases.GenerateThreadDraftResult;
import com.zeromail.core.draft.usecases.GenerateThreadDraftService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.thread.usecases.MarkThreadResolvedService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "thread-draft")
@RequestMapping("/api/threads")
public class ThreadDraftController {

    private static final Logger log = LoggerFactory.getLogger(ThreadDraftController.class);

    private final GenerateThreadDraftService generateThreadDraftService;
    private final MarkThreadResolvedService markThreadResolvedService;

    public ThreadDraftController(
            GenerateThreadDraftService generateThreadDraftService,
            MarkThreadResolvedService markThreadResolvedService) {
        this.generateThreadDraftService = generateThreadDraftService;
        this.markThreadResolvedService = markThreadResolvedService;
    }

    @PostMapping("/{gmailThreadId}/draft")
    public ThreadDraftResponse generateDraft(@PathVariable String gmailThreadId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        GenerateThreadDraftResult result =
                generateThreadDraftService.generateOrRegenerate(
                        new GenerateThreadDraftCommand(tenantId, gmailThreadId));
        log.info(
                "event=thread_draft_requested tenantId={} gmailThreadId={} status={}",
                tenantId,
                gmailThreadId,
                result.status());
        return ThreadDraftResponse.from(result);
    }

    @PostMapping("/{gmailThreadId}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable String gmailThreadId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        markThreadResolvedService.markResolved(tenantId, gmailThreadId);
        log.info(
                "event=thread_marked_resolved tenantId={} gmailThreadId={}",
                tenantId,
                gmailThreadId);
        return ResponseEntity.noContent().build();
    }
}
