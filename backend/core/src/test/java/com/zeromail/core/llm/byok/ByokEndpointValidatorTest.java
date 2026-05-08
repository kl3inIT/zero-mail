package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmByokProperties;
import com.zeromail.core.llm.model.InvalidByokException;

class ByokEndpointValidatorTest {

  @Test
  void rejects_metadata_ip() {
    ByokEndpointValidator validator = defaultValidator();

    assertThatThrownBy(() -> validator.validateOpenAi("https://169.254.169.254/v1"))
        .isInstanceOf(InvalidByokException.class);
  }

  @Test
  void rejects_rfc1918() {
    ByokEndpointValidator validator = defaultValidator();

    assertThatThrownBy(() -> validator.validateOpenAi("https://10.0.0.5/v1"))
        .isInstanceOf(InvalidByokException.class);
  }

  @Test
  void rejects_loopback() {
    ByokEndpointValidator validator = defaultValidator();

    assertThatThrownBy(() -> validator.validateAnthropic("https://127.0.0.1/v1"))
        .isInstanceOf(InvalidByokException.class);
  }

  @Test
  void rejects_non_https() {
    ByokEndpointValidator validator = defaultValidator();

    assertThatThrownBy(() -> validator.validateAnthropic("http://api.anthropic.com/v1"))
        .isInstanceOf(InvalidByokException.class);
  }

  @Test
  void anthropic_default_when_null() {
    ByokEndpointValidator validator = defaultValidator();

    assertThat(validator.validateAnthropic(null)).isEqualTo("https://api.anthropic.com/v1");
  }

  @Test
  void anthropic_rejects_non_vendor_host() {
    ByokEndpointValidator validator = defaultValidator();

    assertThatThrownBy(() -> validator.validateAnthropic("https://example.com/v1"))
        .isInstanceOf(InvalidByokException.class);
  }

  @Test
  void anthropic_compatible_rejects_blank_endpoint() {
    ByokEndpointValidator validator = defaultValidator();

    assertThatThrownBy(() -> validator.validateAnthropicCompatible(" "))
        .isInstanceOf(InvalidByokException.class);
  }

  @Test
  void openai_compat_accepts_with_operator_opt_in() {
    ByokEndpointValidator validator =
        new ByokEndpointValidator(
            new ZeroMailLlmByokProperties(
                true, List.of(), Duration.ofSeconds(5), Duration.ofSeconds(15)));

    assertThat(validator.validateCustomOpenAiCompatible("https://together.xyz/v1"))
        .isEqualTo("https://together.xyz/v1");
  }

  @Test
  void google_genai_default_when_null() {
    ByokEndpointValidator validator = defaultValidator();

    assertThat(validator.validateGoogleGenAi(null))
        .isEqualTo("https://generativelanguage.googleapis.com/v1beta");
  }

  @Test
  void deepseek_default_when_null() {
    ByokEndpointValidator validator = defaultValidator();

    assertThat(validator.validateDeepSeek(null)).isEqualTo("https://api.deepseek.com");
  }

  @Test
  void anthropic_compat_accepts_with_operator_opt_in() {
    ByokEndpointValidator validator =
        new ByokEndpointValidator(
            new ZeroMailLlmByokProperties(
                true, List.of(), Duration.ofSeconds(5), Duration.ofSeconds(15)));

    assertThat(validator.validateAnthropicCompatible("https://example.com/v1"))
        .isEqualTo("https://example.com/v1");
  }

  private static ByokEndpointValidator defaultValidator() {
    return new ByokEndpointValidator(
        new ZeroMailLlmByokProperties(
            false, List.of(), Duration.ofSeconds(5), Duration.ofSeconds(15)));
  }
}
