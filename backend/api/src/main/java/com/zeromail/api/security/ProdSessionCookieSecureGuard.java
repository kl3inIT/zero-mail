package com.zeromail.api.security;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-loud startup gate: when the {@code prod} profile is active, the session-cookie {@code
 * Secure} flag MUST be true. Catches the failure mode where an operator boots the prod profile but
 * forgets to set the explicit cookie configuration, so user session cookies would be sent over
 * plain HTTP.
 *
 * <p>Closes CASA Tier 2 V3.4.1 (Secure cookie attribute) and V14.1.1 (No insecure defaults in
 * prod).
 */
@Component
public class ProdSessionCookieSecureGuard {

    private static final Logger log = LoggerFactory.getLogger(ProdSessionCookieSecureGuard.class);

    private final Environment environment;
    private final boolean sessionCookieSecure;

    public ProdSessionCookieSecureGuard(
            Environment environment,
            @Value("${server.servlet.session.cookie.secure:false}") boolean sessionCookieSecure) {
        this.environment = environment;
        this.sessionCookieSecure = sessionCookieSecure;
    }

    @PostConstruct
    void verifyProdCookieSecure() {
        Set<String> activeProfiles = Set.of(environment.getActiveProfiles());
        if (!activeProfiles.contains("prod")) {
            return;
        }
        if (!sessionCookieSecure) {
            throw new IllegalStateException(
                    "Production profile requires server.servlet.session.cookie.secure=true;"
                            + " CASA V3.4.1 / V14.1.1");
        }
        log.info("event=prod_session_cookie_secure_check passed=true");
    }
}
