package com.zeromail.api.security;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;

import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class TestSessionSupport {

    public static final String SECURITY_CONTEXT_ATTR = "SPRING_SECURITY_CONTEXT";

    public interface TestSessionMinter {
        /** Mints a real Spring Session for the OIDC subject + email and returns the cookie string. */
        String mint(String googleSubject, String email);
    }

    /**
     * Primary in-memory {@link SessionRepository} so Spring Session resolves the test cookie
     * without needing Redis. The bean is marked @Primary to win against any redis-backed
     * SessionRepository auto-configured at boot time.
     */
    @Bean
    @Primary
    SessionRepository<MapSession> testSessionRepository() {
        return new MapSessionRepository(new ConcurrentHashMap<>());
    }

    @Bean
    TestSessionMinter testSessionMinter(SessionRepository<MapSession> sessionRepository) {
        return (googleSubject, email) -> {
            var claims = Map.<String, Object>of(
                    "sub", googleSubject,
                    "email", email);
            var idToken = new OidcIdToken(
                    "test-id-token-" + googleSubject,
                    java.time.Instant.now(),
                    java.time.Instant.now().plusSeconds(3600),
                    claims);
            var principal = new DefaultOidcUser(
                    List.of(new SimpleGrantedAuthority("OIDC_USER")),
                    idToken);
            var authToken = new OAuth2AuthenticationToken(
                    principal,
                    principal.getAuthorities(),
                    "google");
            var securityContext = new SecurityContextImpl(authToken);

            MapSession session = sessionRepository.createSession();
            session.setAttribute(SECURITY_CONTEXT_ATTR, securityContext);
            sessionRepository.save(session);
            return "ZEROMAIL_SESSION=" + session.getId();
        };
    }
}
