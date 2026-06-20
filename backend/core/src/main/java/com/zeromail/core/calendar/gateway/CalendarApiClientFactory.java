package com.zeromail.core.calendar.gateway;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.calendar.exception.CalendarConnectionNotOwnedException;
import com.zeromail.core.calendar.exception.CalendarDisconnectedException;
import com.zeromail.core.calendar.persistence.CalendarConnectionEntity;
import com.zeromail.core.calendar.persistence.CalendarConnectionRepository;
import com.zeromail.core.oauth.token.OAuthTokenStore;
import com.zeromail.core.oauth.token.OAuthTokenStore.RowDiscriminator;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-connection Google Calendar API client builder. Mirrors {@code GmailApiClientFactory}'s shape
 * (access-token cache keyed by connectionId, refresh-token decrypt via {@code OAuthTokenStore},
 * fail-fast on non-CONNECTED status) so callers see the same lifecycle semantics across Gmail and
 * Calendar.
 *
 * <p><b>Pitfall 2 mitigation:</b> reads {@code clientId} / {@code clientSecret} from the {@code
 * google} registration's YAML keys — the calendar registration shares the same GCP OAuth client.
 *
 * <p><b>T-12-01 mitigation:</b> {@link #buildClientForCalendarConnection(UUID, UUID)} only loads
 * rows via {@code findByIdAndTenantId(...)} so a cross-tenant id never surfaces a row; the {@code
 * CalendarConnectionNotOwnedException} carries no {@code google_email}.
 *
 * <p><b>T-12-02 mitigation:</b> {@link #evictAccessToken(UUID)} is exposed for W2's disconnect path
 * so a cached access token cannot outlive the grant it was minted from (CASA V13.1.5 invariant).
 *
 * <p>Privacy logging: only the opaque tenantId + calendarConnectionId UUIDs ever appear in the log
 * stream; refresh-token bytes and access-token bytes are zeroed and never logged.
 */
@Component
public class CalendarApiClientFactory {

    private static final Logger log = LoggerFactory.getLogger(CalendarApiClientFactory.class);

    private static final URI GOOGLE_TOKEN_ENDPOINT =
            URI.create("https://oauth2.googleapis.com/token");

    /**
     * Safety window subtracted from the access-token's reported expiry so the cached token never
     * expires mid-request. Matches the Gmail factory's 60-second window.
     */
    private static final long TOKEN_EXPIRY_SAFETY_WINDOW_SECONDS = 60L;

    private final String clientId;
    private final String clientSecret;
    private final CalendarConnectionRepository calendarConnectionRepository;
    private final OAuthTokenStore oAuthTokenStore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ConcurrentMap<UUID, CachedAccessToken> accessTokenCache =
            new ConcurrentHashMap<>();

    public CalendarApiClientFactory(
            @Value("${spring.security.oauth2.client.registration.google.client-id}")
                    String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}")
                    String clientSecret,
            CalendarConnectionRepository calendarConnectionRepository,
            OAuthTokenStore oAuthTokenStore) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.calendarConnectionRepository =
                Objects.requireNonNull(
                        calendarConnectionRepository, "calendarConnectionRepository");
        this.oAuthTokenStore = Objects.requireNonNull(oAuthTokenStore, "oAuthTokenStore");
    }

    /**
     * Mints a Google Calendar API client for the {@code (tenantId, calendarConnectionId)} pair.
     * Caches the access-token refresh so concurrent calls for the same connection share one decrypt
     * + token-endpoint round-trip.
     *
     * @throws CalendarConnectionNotOwnedException if no row matches the (id, tenantId) pair.
     * @throws CalendarDisconnectedException if the row exists but its status is not {@link
     *     CalendarConnectionStatus#CONNECTED}.
     * @throws IOException on transport / refresh-endpoint failure.
     */
    public Calendar buildClientForCalendarConnection(UUID tenantId, UUID calendarConnectionId)
            throws IOException {
        CalendarConnectionEntity calendarConnection =
                calendarConnectionRepository
                        .findByIdAndTenantId(calendarConnectionId, tenantId)
                        .orElseThrow(
                                () ->
                                        new CalendarConnectionNotOwnedException(
                                                tenantId, calendarConnectionId));
        if (calendarConnection.getStatus() != CalendarConnectionStatus.CONNECTED) {
            accessTokenCache.remove(calendarConnectionId);
            throw new CalendarDisconnectedException(
                    tenantId, calendarConnectionId, calendarConnection.getStatus());
        }
        byte[] refreshTokenEncrypted = calendarConnection.getRefreshTokenEncrypted();
        if (refreshTokenEncrypted == null || refreshTokenEncrypted.length == 0) {
            // CONNECTED status but no usable grant — surface the same domain "not connected"
            // semantics so the controller maps to 409 instead of leaking a 500.
            throw new CalendarDisconnectedException(
                    tenantId, calendarConnectionId, calendarConnection.getStatus());
        }

        CachedAccessToken cached = accessTokenCache.get(calendarConnectionId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return buildCalendarClient(cached.accessToken());
        }

        byte[] refreshTokenBytes =
                oAuthTokenStore.decrypt(
                        refreshTokenEncrypted, tenantId, RowDiscriminator.CALENDAR_CONNECTION);
        try {
            CachedAccessToken refreshed =
                    refreshAccessToken(
                            new String(refreshTokenBytes, StandardCharsets.UTF_8),
                            tenantId,
                            calendarConnectionId);
            accessTokenCache.put(calendarConnectionId, refreshed);
            return buildCalendarClient(refreshed.accessToken());
        } finally {
            Arrays.fill(refreshTokenBytes, (byte) 0);
        }
    }

    /**
     * Evicts any cached access token for the given connection. Called by W2's disconnect path after
     * the cascade lands so a cached, still-valid access token cannot survive the grant.
     */
    public void evictAccessToken(UUID calendarConnectionId) {
        accessTokenCache.remove(calendarConnectionId);
    }

    private Calendar buildCalendarClient(String accessToken) throws IOException {
        try {
            GoogleCredentials credentials =
                    GoogleCredentials.create(new AccessToken(accessToken, null));
            return new Calendar.Builder(
                            GoogleNetHttpTransport.newTrustedTransport(),
                            GsonFactory.getDefaultInstance(),
                            new HttpCredentialsAdapter(credentials))
                    .setApplicationName("ZeroMail")
                    .build();
        } catch (GeneralSecurityException securityException) {
            throw new IOException(
                    "Unable to initialize Calendar HTTP transport", securityException);
        }
    }

    private CachedAccessToken refreshAccessToken(
            String decryptedRefreshToken, UUID tenantId, UUID calendarConnectionId)
            throws IOException {
        String requestBody =
                "grant_type=refresh_token"
                        + "&client_id="
                        + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                        + "&client_secret="
                        + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                        + "&refresh_token="
                        + URLEncoder.encode(decryptedRefreshToken, StandardCharsets.UTF_8);
        HttpRequest tokenRefreshRequest =
                HttpRequest.newBuilder()
                        .uri(GOOGLE_TOKEN_ENDPOINT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();
        HttpResponse<String> tokenResponse;
        try {
            tokenResponse =
                    httpClient.send(tokenRefreshRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Calendar access-token refresh interrupted", interruptedException);
        }

        if (tokenResponse.statusCode() == 200) {
            JsonNode responseBody = objectMapper.readTree(tokenResponse.body());
            String accessToken = responseBody.path("access_token").asString();
            int expiresInSeconds = responseBody.path("expires_in").asInt(3600);
            log.info(
                    "event=calendar_access_token_refreshed tenantId={} calendarConnectionId={}",
                    tenantId,
                    calendarConnectionId);
            return new CachedAccessToken(
                    accessToken,
                    Instant.now()
                            .plusSeconds(expiresInSeconds - TOKEN_EXPIRY_SAFETY_WINDOW_SECONDS));
        }
        throw new IOException(
                "Calendar access-token refresh failed with status: " + tokenResponse.statusCode());
    }

    /** Cached access token + its safety-window expiry. Never logged. */
    record CachedAccessToken(String accessToken, Instant expiresAt) {}
}
