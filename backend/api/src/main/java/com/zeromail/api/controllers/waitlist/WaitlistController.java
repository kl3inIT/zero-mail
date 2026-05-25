package com.zeromail.api.controllers.waitlist;

import com.zeromail.api.dto.waitlist.WaitlistSubscribeRequest;
import com.zeromail.api.dto.waitlist.WaitlistSubscribeResponse;
import com.zeromail.core.shared.crypto.Hashing;
import com.zeromail.core.waitlist.application.WaitlistRateLimiter;
import com.zeromail.core.waitlist.application.WaitlistService;
import com.zeromail.core.waitlist.domain.WaitlistSubscribeResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HexFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public pre-launch waitlist signup endpoint. Mounted at {@code POST /api/waitlist/subscribe} and
 * exposed via {@code SecurityConfig#chain} as {@code permitAll()} so unauthenticated visitors on
 * the marketing landing page can submit their email.
 *
 * <p>Always responds 200 OK with a {@code status} discriminator ({@code ADDED} / {@code
 * ALREADY_REGISTERED} / {@code ALREADY_USER}) — never 409 — to avoid leaking which emails the
 * platform already knows. The honeypot field on {@link WaitlistSubscribeRequest} short-circuits
 * obvious bot traffic before it reaches the database.
 */
@RestController
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final WaitlistRateLimiter waitlistRateLimiter;

    public WaitlistController(
            WaitlistService waitlistService, WaitlistRateLimiter waitlistRateLimiter) {
        this.waitlistService = waitlistService;
        this.waitlistRateLimiter = waitlistRateLimiter;
    }

    @PostMapping("/api/waitlist/subscribe")
    public WaitlistSubscribeResponse subscribe(
            @Valid @RequestBody WaitlistSubscribeRequest request,
            HttpServletRequest servletRequest) {
        if (request.website() != null && !request.website().isBlank()) {
            return WaitlistSubscribeResponse.from(WaitlistSubscribeResult.ADDED);
        }

        String clientIp = resolveClientIp(servletRequest);
        String ipHash = clientIp == null ? null : sha256Hex(clientIp);
        waitlistRateLimiter.checkAllowed(ipHash);

        WaitlistSubscribeResult result =
                waitlistService.subscribe(
                        request.email(),
                        request.source(),
                        clientIp,
                        servletRequest.getHeader("User-Agent"));
        return WaitlistSubscribeResponse.from(result);
    }

    private static String resolveClientIp(HttpServletRequest servletRequest) {
        return servletRequest.getRemoteAddr();
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(Hashing.sha256(value));
    }
}
