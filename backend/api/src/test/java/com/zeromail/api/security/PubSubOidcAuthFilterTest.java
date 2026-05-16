package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.TokenVerifier;
import com.zeromail.api.support.MockGoogleOidcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PubSubOidcAuthFilterTest {

    private static final String AUDIENCE = "https://test.example/internal/pubsub/gmail";
    private static final String SERVICE_ACCOUNT_EMAIL =
            "pubsub-sa@test-project.iam.gserviceaccount.com";
    private static final String ISSUER = "https://accounts.google.com";
    private static final String OIDC_EVENT = "pubsub_oidc";

    private MockGoogleOidcServer oidc;

    @BeforeEach
    void startOidcServer() throws Exception {
        oidc = new MockGoogleOidcServer();
        oidc.start();
    }

    @AfterEach
    void stopOidcServer() {
        oidc.stop();
    }

    @Test
    void validToken_passes() throws Exception {
        PubSubOidcAuthFilter filter = filter();
        MockHttpServletRequest request =
                pubsubRequest(oidc.sign(AUDIENCE, SERVICE_ACCOUNT_EMAIL, ISSUER, 300));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void wrongAudience_returns401() throws Exception {
        MockHttpServletResponse response =
                execute(
                        oidc.sign(
                                "https://wrong.example/push", SERVICE_ACCOUNT_EMAIL, ISSUER, 300));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void wrongEmail_returns401() throws Exception {
        MockHttpServletResponse response =
                execute(
                        oidc.sign(
                                AUDIENCE,
                                "wrong@test-project.iam.gserviceaccount.com",
                                ISSUER,
                                300));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void wrongIssuer_returns401() throws Exception {
        MockHttpServletResponse response =
                execute(oidc.sign(AUDIENCE, SERVICE_ACCOUNT_EMAIL, "https://evil.example", 300));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void expiredToken_returns401() throws Exception {
        MockHttpServletResponse response =
                execute(oidc.sign(AUDIENCE, SERVICE_ACCOUNT_EMAIL, ISSUER, -60));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void badSignature_returns401() throws Exception {
        MockHttpServletResponse response =
                execute(oidc.signWithWrongKey(AUDIENCE, SERVICE_ACCOUNT_EMAIL));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void nonPubSubPath_skipsFilter() throws Exception {
        PubSubOidcAuthFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        request.setServletPath("/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(OIDC_EVENT).contains("pubsub_oidc");
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.isCommitted()).isFalse();
    }

    private MockHttpServletResponse execute(String token) throws Exception {
        PubSubOidcAuthFilter filter = filter();
        MockHttpServletRequest request = pubsubRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private PubSubOidcAuthFilter filter() {
        TokenVerifier tokenVerifier =
                TokenVerifier.newBuilder()
                        .setAudience(AUDIENCE)
                        .setIssuer(ISSUER)
                        .setCertificatesLocation(oidc.jwksUrl())
                        .build();
        return new PubSubOidcAuthFilter(SERVICE_ACCOUNT_EMAIL, tokenVerifier);
    }

    private static MockHttpServletRequest pubsubRequest(String token) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/pubsub/gmail");
        request.setServletPath("/internal/pubsub/gmail");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }
}
