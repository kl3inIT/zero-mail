package com.zeromail.core.gmail.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.shared.privacy.Sensitive;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GmailApiClientFactory {

    private final String clientId;
    private final String clientSecret;
    private final String apiRootUrl;
    private final URI tokenEndpoint;

    public GmailApiClientFactory(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret,
            ZeroMailCoreProperties properties) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiRootUrl = properties.gmail().apiRootUrl();
        this.tokenEndpoint = properties.gmail().oauthTokenUrl();
    }

    public Gmail buildGmailClient(String accessToken) throws IOException {
        try {
            GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));
            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
            return new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    requestInitializer)
                    .setApplicationName("ZeroMail")
                    .setRootUrl(apiRootUrl)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to initialize Gmail HTTP transport", e);
        }
    }

    public record TokenRefreshResult(Sensitive<String> accessToken, Instant expiresAt) {}

    public TokenRefreshResult refreshAccessToken(String decryptedRefreshToken) throws IOException {
        HttpClient httpClient = HttpClient.newHttpClient();
        String body = "grant_type=refresh_token"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&refresh_token=" + URLEncoder.encode(decryptedRefreshToken, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(tokenEndpoint)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Token refresh interrupted", e);
        }

        if (response.statusCode() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response.body());
            String refreshedAccessToken = node.path("access_token").asString();
            int expiresIn = node.path("expires_in").asInt(3600);
            return new TokenRefreshResult(
                    Sensitive.of(refreshedAccessToken),
                    Instant.now().plusSeconds(expiresIn - 60L));
        }
        if (response.statusCode() == 400 && response.body().contains("invalid_grant")) {
            throw new InvalidGrantException("OAuth token revoked");
        }
        throw new IOException("Token refresh failed with status: " + response.statusCode());
    }
}
