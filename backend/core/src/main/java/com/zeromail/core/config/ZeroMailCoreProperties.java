package com.zeromail.core.config;

import java.time.Duration;
import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

@ConfigurationProperties(prefix = "zeromail")
@Validated
public record ZeroMailCoreProperties(
    @Valid @NotNull CryptoProperties crypto,
    @Valid GmailProperties gmail,
    @Valid @NotNull BillingProperties billing) {

  public ZeroMailCoreProperties {
    gmail = gmail == null ? GmailProperties.defaults() : gmail;
  }

  public record CryptoProperties(@NotBlank String refreshTokenKeyBase64) {}

  public record GmailProperties(
      @DefaultValue("https://gmail.googleapis.com/") @NotBlank String apiRootUrl,
      @DefaultValue("https://oauth2.googleapis.com/token") @NotNull URI oauthTokenUrl) {

    static GmailProperties defaults() {
      return new GmailProperties(
          "https://gmail.googleapis.com/", URI.create("https://oauth2.googleapis.com/token"));
    }
  }

  public record BillingProperties(
      @Valid @NotNull BillingSepayProperties sepay,
      @Min(1) @DefaultValue("1000") long vndPerCredit,
      @Min(1) @DefaultValue("5") int maxPendingIntentsPerTenant,
      @DefaultValue("PT24H") Duration intentExpiry) {

    /**
     * Defense-in-depth for accidental Spring placeholder sentinel values. Application yml uses a
     * bare ${SEPAY_WEBHOOK_API_KEY}; this rejects literal default-looking values if one is supplied
     * by mistake.
     */
    public BillingProperties {
      if (sepay != null && sepay.webhookApiKey() != null) {
        String webhookApiKey = sepay.webhookApiKey();
        String lowerCaseWebhookApiKey = webhookApiKey.toLowerCase();
        if (webhookApiKey.startsWith("?")
            || webhookApiKey.startsWith("$")
            || lowerCaseWebhookApiKey.contains("must be set")
            || lowerCaseWebhookApiKey.contains("must be supplied")) {
          throw new IllegalStateException(
              "zeromail.billing.sepay.webhook-api-key looks like an unresolved placeholder default. "
                  + "Set SEPAY_WEBHOOK_API_KEY to the real SePay webhook API key from the "
                  + "deployment secret source.");
        }
      }
    }

    public record BillingSepayProperties(@NotBlank String webhookApiKey) {}

    @Override
    public @NonNull String toString() {
      return "BillingProperties[sepay=****, vndPerCredit="
          + vndPerCredit
          + ", maxPendingIntentsPerTenant="
          + maxPendingIntentsPerTenant
          + ", intentExpiry="
          + intentExpiry
          + "]";
    }
  }

  @Override
  public @NonNull String toString() {
    return "ZeroMailCoreProperties[crypto=****, gmail=" + gmail + ", billing=" + billing + "]";
  }
}
