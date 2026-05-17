package com.zeromail.api.controllers.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.analytics.domain.TimeWindow;
import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.projection.RuleHitProjection;
import com.zeromail.core.analytics.projection.TopSenderProjection;
import com.zeromail.core.analytics.usecases.AnalyticsSummaryQueryService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class AnalyticsControllerContractTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    @MockitoBean AnalyticsSummaryQueryService analyticsSummaryQueryService;

    @BeforeEach
    void configureSummaryProjection() {
        when(analyticsSummaryQueryService.summarize(any(UUID.class), any(TimeWindow.class)))
                .thenReturn(
                        new AnalyticsSummaryProjection(
                                7,
                                3,
                                220,
                                List.of(new TopSenderProjection("founder@example.test", 4)),
                                List.of(new RuleHitProjection("Founder Rule", 5, 3, 1))));
    }

    @Test
    void summary_defaults_to_seven_day_window_when_window_is_missing() {
        Seed seed = seedUser("analytics-default");
        Instant beforeRequest = Instant.now();

        ResponseEntity<String> response = get("/api/analytics/summary", seed);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .contains("\"window\":\"7d\"")
                .contains("\"volumeObserved\":7")
                .contains("\"volumeApplied\":3")
                .contains("\"timeSavedSeconds\":220")
                .contains("\"senderEmail\":\"founder@example.test\"")
                .contains("\"ruleName\":\"Founder Rule\"")
                .contains("\"applied\":3");
        assertSummarizeCalledWith(seed.tenantId(), Duration.ofDays(7), beforeRequest);
    }

    @Test
    void summary_accepts_explicit_thirty_day_window() {
        Seed seed = seedUser("analytics-thirty");
        Instant beforeRequest = Instant.now();

        ResponseEntity<String> response = get("/api/analytics/summary?window=30d", seed);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"window\":\"30d\"");
        assertSummarizeCalledWith(seed.tenantId(), Duration.ofDays(30), beforeRequest);
    }

    @Test
    void summary_defaults_blank_windows_to_seven_days() {
        Seed seed = seedUser("analytics-blank");

        ResponseEntity<String> emptyWindowResponse = get("/api/analytics/summary?window=", seed);
        ResponseEntity<String> whitespaceWindowResponse = getSummaryWithWindowParameter("  ", seed);

        assertThat(emptyWindowResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(emptyWindowResponse.getBody()).contains("\"window\":\"7d\"");
        assertThat(whitespaceWindowResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(whitespaceWindowResponse.getBody()).contains("\"window\":\"7d\"");
    }

    @Test
    void summary_rejects_unknown_nonblank_window() {
        Seed seed = seedUser("analytics-invalid");

        ResponseEntity<String> response = get("/api/analytics/summary?window=bogus", seed);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .startsWith("application/problem+json");
        assertThat(response.getBody())
                .contains("\"code\":\"error.badRequest\"")
                .contains("\"message\":\"error.badRequest\"");
        verifyNoInteractions(analyticsSummaryQueryService);
    }

    @Test
    void summary_requires_authentication() {
        ResponseEntity<String> response = getWithoutAuthentication("/api/analytics/summary");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void openapi_docs_include_analytics_summary_path() {
        ResponseEntity<String> response = getWithoutAuthentication("/v3/api-docs");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .contains("\"/api/analytics/summary\"")
                .contains("\"window\"");
    }

    private void assertSummarizeCalledWith(
            UUID expectedTenantId, Duration expectedDuration, Instant beforeRequest) {
        ArgumentCaptor<TimeWindow> timeWindowCaptor = ArgumentCaptor.forClass(TimeWindow.class);
        verify(analyticsSummaryQueryService)
                .summarize(eq(expectedTenantId), timeWindowCaptor.capture());
        TimeWindow capturedTimeWindow = timeWindowCaptor.getValue();
        assertThat(
                        Duration.between(
                                capturedTimeWindow.startInclusive(),
                                capturedTimeWindow.endExclusive()))
                .isEqualTo(expectedDuration);
        assertThat(capturedTimeWindow.endExclusive())
                .isBetween(beforeRequest.minusSeconds(1), Instant.now().plusSeconds(1));
    }

    private ResponseEntity<String> get(String uri, Seed seed) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(uri)
                .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {})
                .toEntity(String.class);
    }

    private ResponseEntity<String> getSummaryWithWindowParameter(String window, Seed seed) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/api/analytics/summary")
                                        .queryParam("window", window)
                                        .build())
                .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {})
                .toEntity(String.class);
    }

    private ResponseEntity<String> getWithoutAuthentication(String uri) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {})
                .toEntity(String.class);
    }

    private Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        String uniqueLabel = label + "-" + UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, uniqueLabel));
        String googleSubject = "sub-" + uniqueLabel;
        String email = uniqueLabel + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                userRepository.save(
                                        new UserEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                googleSubject,
                                                email)));
        return new Seed(tenantId, googleSubject, email);
    }

    private record Seed(UUID tenantId, String googleSubject, String email) {}
}
