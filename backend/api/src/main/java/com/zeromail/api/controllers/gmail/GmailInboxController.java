package com.zeromail.api.controllers.gmail;

import com.zeromail.api.dto.gmail.GmailInboxMessageDetailResponse;
import com.zeromail.api.dto.gmail.GmailInboxPageResponse;
import com.zeromail.core.gmail.usecases.RecentInboxReadService;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableException;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxUnavailableReason;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "gmail-inbox")
@RequestMapping("/api/gmail/inbox")
public class GmailInboxController {

    private final RecentInboxReadService recentInboxReadService;
    private final TriageGmailWriter triageGmailWriter;

    public GmailInboxController(
            RecentInboxReadService recentInboxReadService, TriageGmailWriter triageGmailWriter) {
        this.recentInboxReadService = recentInboxReadService;
        this.triageGmailWriter = triageGmailWriter;
    }

    @GetMapping
    public GmailInboxPageResponse list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        UUID tenantId = TenantContext.currentTenantUuid();
        try {
            return GmailInboxPageResponse.from(
                    recentInboxReadService.fetchPage(tenantId, cursor, limit));
        } catch (RecentInboxUnavailableException inboxUnavailableException) {
            throw responseStatusException(inboxUnavailableException);
        }
    }

    @GetMapping("/{gmailMessageId}")
    public GmailInboxMessageDetailResponse detail(@PathVariable String gmailMessageId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        try {
            return GmailInboxMessageDetailResponse.from(
                    recentInboxReadService.fetchMessageDetail(tenantId, gmailMessageId));
        } catch (RecentInboxUnavailableException inboxUnavailableException) {
            throw responseStatusException(inboxUnavailableException);
        }
    }

    @PostMapping("/{gmailMessageId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable String gmailMessageId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        try {
            triageGmailWriter.markRead(tenantId, gmailMessageId);
        } catch (IOException ioException) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Gmail inbox unavailable", ioException);
        }
    }

    private static ResponseStatusException responseStatusException(
            RecentInboxUnavailableException inboxUnavailableException) {
        return new ResponseStatusException(
                statusFor(inboxUnavailableException.reason()),
                "Gmail inbox unavailable",
                inboxUnavailableException);
    }

    private static HttpStatus statusFor(RecentInboxUnavailableReason reason) {
        return switch (reason) {
            case INVALID_CURSOR -> HttpStatus.BAD_REQUEST;
            case NOT_CONNECTED -> HttpStatus.CONFLICT;
            case DISCONNECTED, NO_READ_GRANT, REVOKED -> HttpStatus.FORBIDDEN;
            case MESSAGE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case GMAIL_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
    }
}
