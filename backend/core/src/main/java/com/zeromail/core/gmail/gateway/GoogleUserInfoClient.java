package com.zeromail.core.gmail.gateway;

import com.zeromail.core.shared.privacy.Sensitive;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GoogleUserInfoClient {

    private static final String USERINFO_BASE_URL = "https://www.googleapis.com/oauth2/v3";

    private final RestClient userInfoRestClient;

    public GoogleUserInfoClient(RestClient.Builder restClientBuilder) {
        this.userInfoRestClient = restClientBuilder.baseUrl(USERINFO_BASE_URL).build();
    }

    public Optional<GoogleUserProfile> fetch(Sensitive<String> accessToken) {
        try {
            GoogleUserInfoResponse response =
                    userInfoRestClient
                            .get()
                            .uri("/userinfo")
                            .headers(headers -> headers.setBearerAuth(accessToken.value()))
                            .retrieve()
                            .body(GoogleUserInfoResponse.class);
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(
                    new GoogleUserProfile(
                            clean(response.email()),
                            clean(response.name()),
                            clean(response.picture())));
        } catch (RestClientException | IllegalArgumentException userInfoFailure) {
            return Optional.empty();
        }
    }

    public record GoogleUserProfile(String email, String name, String picture) {}

    private record GoogleUserInfoResponse(String email, String name, String picture) {}

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
