package com.zeromail.api.security;

import com.zeromail.core.admin.auth.usecases.EnrollmentTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class EnrollmentSessionController {

    public static final String ENROLLMENT_ADMIN_USER_ID_SESSION_ATTRIBUTE =
            "enrollment.adminUserId";

    private static final Duration ENROLLMENT_SESSION_TTL = Duration.ofMinutes(10);

    private final EnrollmentTokenService enrollmentTokenService;
    private final Clock clock;

    public EnrollmentSessionController(EnrollmentTokenService enrollmentTokenService, Clock clock) {
        this.enrollmentTokenService = enrollmentTokenService;
        this.clock = clock;
    }

    // see docs/ops/admin-interface-freeze.md §Enrollment Routing
    @PostMapping("/api/admin/enrollment/session")
    EnrollmentSessionResponse createEnrollmentSession(
            @RequestBody EnrollmentSessionRequest enrollmentSessionRequest,
            HttpServletRequest httpServletRequest) {
        UUID adminUserId =
                enrollmentTokenService
                        .consume(enrollmentSessionRequest.token(), enrollmentSessionRequest.email())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.GONE,
                                                "error.admin.enrollment_token_expired"));
        HttpSession httpSession = httpServletRequest.getSession(true);
        httpSession.setMaxInactiveInterval((int) ENROLLMENT_SESSION_TTL.toSeconds());
        httpSession.setAttribute(
                ENROLLMENT_ADMIN_USER_ID_SESSION_ATTRIBUTE, adminUserId.toString());
        return new EnrollmentSessionResponse(
                "ZEROMAIL_ADMIN", clock.instant().plus(ENROLLMENT_SESSION_TTL));
    }

    record EnrollmentSessionRequest(String token, String email) {}

    record EnrollmentSessionResponse(String enrollmentSessionCookie, Instant expiresAt) {}
}
