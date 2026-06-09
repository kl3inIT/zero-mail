package com.zeromail.core.gmail.gateway;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.zeromail.core.gmail.config.GmailProperties;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.shared.privacy.Sensitive;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GmailApiClientFactory {

    private final String clientId;
    private final String clientSecret;
    private final String apiRootUrl;
    private final URI tokenEndpoint;
    private final GmailConnectionRepository gmailConnectionRepository;
    private final RefreshTokenCipher refreshTokenCipher;
    // Caches the access token result by gmailConnectionId so two mailboxes in one tenant never
    // share access tokens while still skipping repeated AES-GCM decrypt + Google refresh calls.
    private final ConcurrentMap<UUID, TokenRefreshResult> accessTokenCache =
            new ConcurrentHashMap<>();

    public GmailApiClientFactory(
            @Value("${spring.security.oauth2.client.registration.google.client-id}")
                    String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}")
                    String clientSecret,
            GmailProperties gmailProperties,
            GmailConnectionRepository gmailConnectionRepository,
            RefreshTokenCipher refreshTokenCipher) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiRootUrl = gmailProperties.apiRootUrl();
        this.tokenEndpoint = gmailProperties.oauthTokenUrl();
        this.gmailConnectionRepository = gmailConnectionRepository;
        this.refreshTokenCipher = refreshTokenCipher;
    }

    public Gmail buildGmailClient(String accessToken) throws IOException {
        return buildGmailClient(accessToken, null);
    }

    public Gmail buildGmailClient(String accessToken, Duration requestTimeout) throws IOException {
        try {
            GoogleCredentials credentials =
                    GoogleCredentials.create(new AccessToken(accessToken, null));
            HttpRequestInitializer requestInitializer =
                    withOptionalTimeout(new HttpCredentialsAdapter(credentials), requestTimeout);
            return new Gmail.Builder(
                            GoogleNetHttpTransport.newTrustedTransport(),
                            GsonFactory.getDefaultInstance(),
                            requestInitializer)
                    .setApplicationName("ZeroMail")
                    .setRootUrl(apiRootUrl)
                    .build();
        } catch (GeneralSecurityException securityException) {
            throw new IOException("Unable to initialize Gmail HTTP transport", securityException);
        }
    }

    public Gmail buildClientForMailbox(MailboxRef mailboxRef) throws IOException {
        return buildClientForMailbox(mailboxRef, null);
    }

    public Gmail buildClientForMailbox(MailboxRef mailboxRef, Duration requestTimeout)
            throws IOException {
        MailboxRef resolvedMailboxRef =
                Objects.requireNonNull(mailboxRef, "mailboxRef must not be null");
        GmailConnectionEntity gmailConnection =
                gmailConnectionRepository
                        .findByIdAndTenantId(
                                resolvedMailboxRef.gmailConnectionId(),
                                resolvedMailboxRef.tenantId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No Gmail mailbox for tenantId: "
                                                        + resolvedMailboxRef.tenantId()
                                                        + ", gmailConnectionId: "
                                                        + resolvedMailboxRef.gmailConnectionId()));
        requireConnectedGrant(gmailConnection, resolvedMailboxRef.tenantId());
        return buildClientForConnection(
                gmailConnection, resolvedMailboxRef.tenantId(), requestTimeout);
    }

    @Deprecated(forRemoval = true)
    public Gmail buildClientForTenant(UUID tenantId) throws IOException {
        return buildClientForTenant(tenantId, null);
    }

    @Deprecated(forRemoval = true)
    public Gmail buildClientForTenant(UUID tenantId, Duration requestTimeout) throws IOException {
        List<GmailConnectionEntity> connectedMailboxes =
                gmailConnectionRepository.findByTenantIdOrderByIsPrimaryDesc(tenantId).stream()
                        .filter(
                                gmailConnection ->
                                        gmailConnection.getStatus()
                                                == GmailConnectionStatus.CONNECTED)
                        .toList();
        if (connectedMailboxes.isEmpty()) {
            throw new IllegalStateException("No connected Gmail mailbox for tenantId: " + tenantId);
        }
        if (connectedMailboxes.size() > 1) {
            throw new IllegalStateException(
                    "Tenant has more than one connected Gmail mailbox; use buildClientForMailbox");
        }
        GmailConnectionEntity gmailConnection = connectedMailboxes.getFirst();
        return buildClientForConnection(gmailConnection, tenantId, requestTimeout);
    }

    public Gmail buildClientForConnection(GmailConnectionEntity gmailConnection, UUID tenantId)
            throws IOException {
        return buildClientForConnection(gmailConnection, tenantId, null);
    }

    public Gmail buildClientForConnection(
            GmailConnectionEntity gmailConnection, UUID tenantId, Duration requestTimeout)
            throws IOException {
        UUID gmailConnectionId = gmailConnection.getId();
        TokenRefreshResult cachedToken = accessTokenCache.get(gmailConnectionId);
        if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now())) {
            return buildGmailClient(cachedToken.accessToken().value(), requestTimeout);
        }
        byte[] decryptedRefreshTokenBytes =
                refreshTokenCipher.decrypt(
                        gmailConnection.getRefreshTokenEncrypted(), tenantId.toString());
        try {
            String decryptedRefreshToken =
                    new String(decryptedRefreshTokenBytes, StandardCharsets.UTF_8);
            TokenRefreshResult tokenResult;
            try {
                tokenResult = refreshAccessToken(decryptedRefreshToken);
            } catch (InvalidGrantException invalidGrant) {
                accessTokenCache.remove(gmailConnectionId);
                throw invalidGrant;
            }
            accessTokenCache.put(gmailConnectionId, tokenResult);
            return buildGmailClient(tokenResult.accessToken().value(), requestTimeout);
        } finally {
            Arrays.fill(decryptedRefreshTokenBytes, (byte) 0);
        }
    }

    private static void requireConnectedGrant(
            GmailConnectionEntity gmailConnection, UUID tenantId) {
        if (gmailConnection.getStatus() != GmailConnectionStatus.CONNECTED) {
            throw new IllegalStateException(
                    "Gmail mailbox is not connected for tenantId: "
                            + tenantId
                            + ", gmailConnectionId: "
                            + gmailConnection.getId());
        }
        byte[] refreshTokenEncrypted = gmailConnection.getRefreshTokenEncrypted();
        if (refreshTokenEncrypted == null || refreshTokenEncrypted.length == 0) {
            throw new IllegalStateException(
                    "Gmail mailbox has no usable grant for tenantId: "
                            + tenantId
                            + ", gmailConnectionId: "
                            + gmailConnection.getId());
        }
    }

    public record TokenRefreshResult(Sensitive<String> accessToken, Instant expiresAt) {}

    public TokenRefreshResult refreshAccessToken(String decryptedRefreshToken) throws IOException {
        HttpClient httpClient = HttpClient.newHttpClient();
        String requestBody =
                "grant_type=refresh_token"
                        + "&client_id="
                        + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                        + "&client_secret="
                        + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                        + "&refresh_token="
                        + URLEncoder.encode(decryptedRefreshToken, StandardCharsets.UTF_8);
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(tokenEndpoint)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException("Token refresh interrupted", interruptedException);
        }

        if (response.statusCode() == 200) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseBody = objectMapper.readTree(response.body());
            String refreshedAccessToken = responseBody.path("access_token").asString();
            int expiresInSeconds = responseBody.path("expires_in").asInt(3600);
            return new TokenRefreshResult(
                    Sensitive.of(refreshedAccessToken),
                    Instant.now().plusSeconds(expiresInSeconds - 60L));
        }
        if (response.statusCode() == 400 && response.body().contains("invalid_grant")) {
            throw new InvalidGrantException("OAuth token revoked");
        }
        throw new IOException("Token refresh failed with status: " + response.statusCode());
    }

    private static HttpRequestInitializer withOptionalTimeout(
            HttpRequestInitializer requestInitializer, Duration requestTimeout) {
        if (requestTimeout == null) {
            return requestInitializer;
        }
        int timeoutMillis = timeoutMillis(requestTimeout);
        return request -> {
            requestInitializer.initialize(request);
            request.setConnectTimeout(timeoutMillis);
            request.setReadTimeout(timeoutMillis);
        };
    }

    private static int timeoutMillis(Duration requestTimeout) {
        Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        long timeoutMillis = requestTimeout.toMillis();
        if (timeoutMillis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("requestTimeout must fit in an int millisecond");
        }
        return Math.toIntExact(Math.max(1L, timeoutMillis));
    }
}
