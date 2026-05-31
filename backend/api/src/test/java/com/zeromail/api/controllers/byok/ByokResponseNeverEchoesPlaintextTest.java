package com.zeromail.api.controllers.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.zeromail.core.llm.byok.BaseUrlValidator;
import com.zeromail.core.llm.domain.LlmProvider;
import com.zeromail.core.llm.domain.MasterKeyTestResult;
import com.zeromail.core.llm.gateway.springai.ConnectionTestResult;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ByokResponseNeverEchoesPlaintextTest extends ByokControllerApiTestSupport {

    private static final Pattern LONG_KEY_PATTERN = Pattern.compile("sk-[A-Za-z0-9]{20,}");

    @Test
    void byokResponsesNeverEchoPlaintextApiKeys() {
        Seed seed = seedUser("byok-response-leak");
        String plaintextKey = "sk-ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        when(providerConnectionTester.probeConnection(
                        eq(LlmProvider.OPENAI),
                        any(BaseUrlValidator.ValidatedTarget.class),
                        any(byte[].class)))
                .thenReturn(new ConnectionTestResult(MasterKeyTestResult.OK, List.of("gpt-4o")));

        ResponseEntity<String> saveResponse =
                postResponse(
                        authenticatedClient(seed),
                        "/api/byok",
                        Map.of(
                                "provider",
                                "OPENAI",
                                "baseUrl",
                                "https://api.openai.com/v1",
                                "apiKey",
                                plaintextKey));
        assertNoPlaintext(saveResponse.getBody());

        ResponseEntity<String> getResponse = getResponse(authenticatedClient(seed), "/api/byok");
        assertNoPlaintext(getResponse.getBody());

        postResponse(authenticatedClient(seed), "/api/byok/test-connection", Map.of());
        putResponse(authenticatedClient(seed), "/api/byok/model", Map.of("modelId", "gpt-4o"));
        ResponseEntity<String> activateResponse =
                putResponse(authenticatedClient(seed), "/api/byok/active", Map.of("active", true));
        assertNoPlaintext(activateResponse.getBody());
    }

    private static void assertNoPlaintext(String responseBody) {
        assertThat(responseBody).doesNotContain("ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890");
        assertThat(LONG_KEY_PATTERN.matcher(responseBody).find()).isFalse();
    }
}
