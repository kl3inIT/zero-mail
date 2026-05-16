package com.zeromail.api.loadtest;

import com.google.auth.oauth2.TokenVerifier;
import com.zeromail.api.security.PubSubTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("loadtest")
@ConditionalOnProperty(name = "zeromail.loadtest.enabled", havingValue = "true")
public class LoadtestPubsubVerifierConfig {

    @Bean
    @Primary
    public PubSubTokenVerifier loadtestTokenVerifier(
            @Value("${zero-mail.api.gmail.pubsub.sa-principal-email}") String serviceAccountEmail) {
        return idToken -> verifiedEmail(idToken, serviceAccountEmail);
    }

    private static String verifiedEmail(String idToken, String serviceAccountEmail)
            throws TokenVerifier.VerificationException {
        if (idToken == null || idToken.isBlank()) {
            throw new TokenVerifier.VerificationException("loadtest token must not be blank");
        }
        return serviceAccountEmail;
    }
}
