package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.BillingProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.BillingProperties.BillingSepayProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.CryptoProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.LlmProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmByokProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.model.LlmChatRequest;
import com.zeromail.core.llm.model.SafetyViolationException;
import com.zeromail.core.llm.model.SystemPrompts;

class GoogleGenAiByokModelClientTest {

  @Test
  void rejects_tool_required_requests_until_google_genai_exposes_required_tool_choice() {
    GoogleGenAiByokModelClient modelClient =
        new GoogleGenAiByokModelClient(new ByokEndpointValidator(properties()), properties());

    assertThatThrownBy(
            () ->
                modelClient.call(
                    "google-key".getBytes(),
                    "https://generativelanguage.googleapis.com/v1beta",
                    new LlmChatRequest(
                        SystemPrompts.TRIAGE_SYSTEM_PROMPT,
                        "sanitized-user-message",
                        List.of(),
                        "gemini-2.0-flash",
                        0.0,
                        true)))
        .isInstanceOf(SafetyViolationException.class);
  }

  private static ZeroMailCoreProperties properties() {
    return new ZeroMailCoreProperties(
        new CryptoProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
        null,
        new BillingProperties(
            new BillingSepayProperties("test-sepay-key-fixture"), 1000, 5, Duration.ofHours(24)),
        new LlmProperties(
            new ZeroMailLlmProperties(
                null,
                null,
                "test-platform-key",
                null,
                null,
                null,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)),
            new ZeroMailLlmByokProperties(
                false, List.of(), Duration.ofSeconds(5), Duration.ofSeconds(15))));
  }
}
