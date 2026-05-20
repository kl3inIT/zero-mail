package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.queue.DeadLetterPageResponse;
import com.zeromail.api.dto.admin.queue.QueueHealthResponse;
import com.zeromail.api.dto.admin.queue.RequeueRequest;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.queue.usecases.DeadLetterRequeueService;
import com.zeromail.core.admin.queue.usecases.QueueHealthQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-queue")
@RequestMapping("/api/admin/queue")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQueueController {

    private final QueueHealthQueryService queueHealthQueryService;
    private final DeadLetterRequeueService deadLetterRequeueService;

    public AdminQueueController(
            QueueHealthQueryService queueHealthQueryService,
            DeadLetterRequeueService deadLetterRequeueService) {
        this.queueHealthQueryService =
                Objects.requireNonNull(
                        queueHealthQueryService, "queueHealthQueryService must not be null");
        this.deadLetterRequeueService =
                Objects.requireNonNull(
                        deadLetterRequeueService, "deadLetterRequeueService must not be null");
    }

    @GetMapping("/health")
    public QueueHealthResponse health() {
        AdminContext.currentOrThrow();
        return QueueHealthResponse.from(queueHealthQueryService.snapshot());
    }

    @GetMapping("/dead-letters")
    public DeadLetterPageResponse deadLetters(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit) {
        AdminContext.currentOrThrow();
        return DeadLetterPageResponse.from(queueHealthQueryService.deadLetterPage(cursor, limit));
    }

    @PostMapping("/dead-letters/{jobId}/requeue")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requeue(
            @PathVariable UUID jobId,
            @Valid @RequestBody RequeueRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        deadLetterRequeueService.requeue(
                jobId, request.reason(), httpServletRequest.getRemoteAddr(), UUID.randomUUID());
    }
}
