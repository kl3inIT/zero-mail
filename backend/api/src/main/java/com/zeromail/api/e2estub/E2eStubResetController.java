package com.zeromail.api.e2estub;

import com.zeromail.core.account.usecases.OAuthProvisioningService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("e2e-stub")
@ConditionalOnProperty(name = "zeromail.e2e-stub.enabled", havingValue = "true")
public class E2eStubResetController {

    private static final Logger log = LoggerFactory.getLogger(E2eStubResetController.class);
    private static final String GOOGLE_SUBJECT = "e2e-stub-user-1";
    private static final String EMAIL = "e2e-stub-user-1@e2e-stub.invalid";
    private static final String REFRESH_TOKEN = "e2e-stub-refresh-token";
    private static final String GRANTED_GMAIL_SCOPES =
            "https://www.googleapis.com/auth/gmail.modify";
    private static final String SESSION_COOKIE_NAME = "ZEROMAIL_SESSION";
    private static final String SESSION_COOKIE_VALUE = "e2e-stub-session";

    private final E2eStubGmailApiClientFactory e2eStubGmailFactory;
    private final OAuthProvisioningService oauthProvisioningService;

    public E2eStubResetController(
            E2eStubGmailApiClientFactory e2eStubGmailFactory,
            OAuthProvisioningService oauthProvisioningService) {
        this.e2eStubGmailFactory = e2eStubGmailFactory;
        this.oauthProvisioningService = oauthProvisioningService;
    }

    @PostMapping("/api/test/e2e-stub/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        e2eStubGmailFactory.reset();
        log.info("event=e2e_stub_reset");
    }

    @PostMapping("/api/test/e2e-stub/seed-message")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seedMessage(@RequestBody SeedMessageRequest request) {
        e2eStubGmailFactory.seedMessage(request);
        log.info("event=e2e_stub_seed_message");
    }

    @PostMapping("/api/test/e2e-stub/seed-session")
    public SeedSessionResponse seedSession() {
        OAuthProvisioningService.BundledProvisioningResult provisioningResult =
                oauthProvisioningService.provisionBundledOAuth(
                        GOOGLE_SUBJECT, EMAIL, REFRESH_TOKEN, GRANTED_GMAIL_SCOPES);
        UUID tenantId = provisioningResult.tenantId();
        log.info("event=e2e_stub_seed_session tenantId={}", tenantId);
        return new SeedSessionResponse(
                tenantId.toString(),
                GOOGLE_SUBJECT,
                EMAIL,
                SESSION_COOKIE_NAME,
                SESSION_COOKIE_VALUE);
    }

    @GetMapping("/api/test/e2e-stub/drafts")
    public DraftResponse draft(@RequestParam("messageId") String messageId) {
        E2eStubGmailApiClientFactory.SeededDraft seededDraft =
                e2eStubGmailFactory.findDraft(messageId);
        log.info("event=e2e_stub_drafts_query");
        if (seededDraft == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return new DraftResponse(
                seededDraft.draftId(), seededDraft.messageId(), seededDraft.body());
    }

    public record SeedSessionResponse(
            String tenantId,
            String googleSubject,
            String email,
            String sessionCookieName,
            String sessionCookieValue) {}

    public record DraftResponse(String draftId, String messageId, String body) {}
}
