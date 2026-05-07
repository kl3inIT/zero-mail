package com.zeromail.core.llm.service;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.byok.ByokEndpointValidator;
import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.llm.model.ByokCurrent;
import com.zeromail.core.llm.model.ByokSaveCommand;
import com.zeromail.core.llm.model.ByokSaveResult;
import com.zeromail.core.llm.model.ByokValidateCommand;
import com.zeromail.core.llm.model.ByokValidateResult;
import com.zeromail.core.llm.model.InvalidByokException;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;

@Service
public class ByokService {

  private static final Logger log = LoggerFactory.getLogger(ByokService.class);
  private static final String ANTHROPIC_VERSION = "2023-06-01";
  private static final String ANTHROPIC_VALIDATION_MODEL = "claude-3-haiku-20240307";

  private final TenantByokCredentialsRepository tenantByokCredentialsRepository;
  private final RefreshTokenCipher refreshTokenCipher;
  private final ByokEndpointValidator byokEndpointValidator;
  private final RestClient.Builder restClientBuilder;

  public ByokService(
      TenantByokCredentialsRepository tenantByokCredentialsRepository,
      RefreshTokenCipher refreshTokenCipher,
      ByokEndpointValidator byokEndpointValidator,
      RestClient.Builder restClientBuilder) {
    this.tenantByokCredentialsRepository = tenantByokCredentialsRepository;
    this.refreshTokenCipher = refreshTokenCipher;
    this.byokEndpointValidator = byokEndpointValidator;
    this.restClientBuilder = restClientBuilder;
  }

  public ByokValidateResult validate(UUID tenantId, ByokValidateCommand command) {
    String canonicalEndpoint = canonicalEndpointFor(command.provider(), command.endpoint());
    log.info("event=byok_validate_attempted tenantId={} provider={}", tenantId, command.provider());
    ByokValidateResult result =
        probeUpstream(tenantId, command.provider(), canonicalEndpoint, command.apiKey());
    if (result.ok()) {
      int modelsCount = result.models() == null ? 0 : result.models().size();
      log.info(
          "event=byok_validate_succeeded tenantId={} provider={} modelsCount={}",
          tenantId,
          command.provider(),
          modelsCount);
    } else {
      log.info(
          "event=byok_validate_failed tenantId={} provider={} reason={}",
          tenantId,
          command.provider(),
          result.reason());
    }
    return result;
  }

  /**
   * Saves BYOK credentials only after server-side endpoint validation and an upstream key probe.
   * This intentionally re-runs the provider probe even if the browser already called validate, so a
   * direct POST to save cannot persist an unvalidated or revoked key.
   */
  @Transactional
  public ByokSaveResult save(UUID tenantId, ByokSaveCommand command) {
    String canonicalEndpoint = canonicalEndpointFor(command.provider(), command.endpoint());
    ByokValidateResult upstreamValidation =
        validateUpstreamKey(tenantId, command.provider(), canonicalEndpoint, command.apiKey());
    if (!upstreamValidation.ok()) {
      throw new InvalidByokException();
    }

    byte[] plaintextKey = command.apiKey().getBytes(StandardCharsets.UTF_8);
    byte[] encryptedEnvelope;
    try {
      encryptedEnvelope = refreshTokenCipher.encrypt(plaintextKey, tenantId.toString());
    } finally {
      Arrays.fill(plaintextKey, (byte) 0);
    }
    short keyVersion = keyVersionFromEnvelope(encryptedEnvelope);
    TenantByokCredentialsEntity credentials =
        tenantByokCredentialsRepository
            .findByTenantId(tenantId)
            .map(
                existingCredentials -> {
                  existingCredentials.replaceCredential(
                      command.provider(), canonicalEndpoint, encryptedEnvelope, keyVersion);
                  return existingCredentials;
                })
            .orElseGet(
                () ->
                    new TenantByokCredentialsEntity(
                        UUID.randomUUID(),
                        tenantId,
                        command.provider(),
                        canonicalEndpoint,
                        encryptedEnvelope,
                        keyVersion));
    TenantByokCredentialsEntity savedCredentials =
        tenantByokCredentialsRepository.saveAndFlush(credentials);
    Instant savedAt =
        savedCredentials.getUpdatedAt() == null ? Instant.now() : savedCredentials.getUpdatedAt();
    return new ByokSaveResult(true, savedAt);
  }

  @Transactional(readOnly = true)
  public Optional<ByokCurrent> current(UUID tenantId) {
    return tenantByokCredentialsRepository
        .findByTenantId(tenantId)
        .map(
            credentials ->
                new ByokCurrent(
                    credentials.getProvider(),
                    endpointHost(credentials.getEndpoint()),
                    credentials.getUpdatedAt() == null
                        ? credentials.getCreatedAt()
                        : credentials.getUpdatedAt()));
  }

  private ByokValidateResult validateUpstreamKey(
      UUID tenantId, BYOKProvider provider, String canonicalEndpoint, String apiKey) {
    return probeUpstream(tenantId, provider, canonicalEndpoint, apiKey);
  }

  private ByokValidateResult probeUpstream(
      UUID tenantId, BYOKProvider provider, String canonicalEndpoint, String apiKey) {
    try {
      return switch (provider) {
        case OPENAI_COMPATIBLE -> probeOpenAiCompatible(canonicalEndpoint, apiKey);
        case ANTHROPIC -> probeAnthropic(canonicalEndpoint, apiKey);
      };
    } catch (RestClientResponseException upstreamRejection) {
      return new ByokValidateResult(false, null, "upstream_rejected");
    } catch (ResourceAccessException resourceAccessFailure) {
      return new ByokValidateResult(
          false, null, isTimeout(resourceAccessFailure) ? "timeout" : "connection_failed");
    } catch (RestClientException restClientFailure) {
      return new ByokValidateResult(false, null, "connection_failed");
    } catch (RuntimeException unexpectedFailure) {
      log.info(
          "event=byok_validate_failed tenantId={} provider={} reason={}",
          tenantId,
          provider,
          unexpectedFailure.getClass().getSimpleName());
      return new ByokValidateResult(false, null, "connection_failed");
    }
  }

  private ByokValidateResult probeOpenAiCompatible(String canonicalEndpoint, String apiKey) {
    OpenAiModelsResponse response =
        restClientBuilder
            .build()
            .get()
            .uri(joinPath(canonicalEndpoint, "models"))
            .headers(headers -> headers.setBearerAuth(apiKey))
            .retrieve()
            .body(OpenAiModelsResponse.class);
    List<String> modelIds =
        response == null || response.data() == null
            ? List.of()
            : response.data().stream().map(OpenAiModel::id).toList();
    return new ByokValidateResult(true, modelIds, null);
  }

  private ByokValidateResult probeAnthropic(String canonicalEndpoint, String apiKey) {
    restClientBuilder
        .build()
        .post()
        .uri(joinPath(canonicalEndpoint, "messages"))
        .contentType(MediaType.APPLICATION_JSON)
        .header("x-api-key", apiKey)
        .header("anthropic-version", ANTHROPIC_VERSION)
        .body(
            Map.of(
                "model",
                ANTHROPIC_VALIDATION_MODEL,
                "max_tokens",
                1,
                "messages",
                List.of(Map.of("role", "user", "content", "."))))
        .retrieve()
        .toBodilessEntity();
    return new ByokValidateResult(true, null, null);
  }

  private String canonicalEndpointFor(BYOKProvider provider, String endpoint) {
    return switch (provider) {
      case ANTHROPIC -> byokEndpointValidator.validateAnthropic(endpoint);
      case OPENAI_COMPATIBLE -> byokEndpointValidator.validateOpenAiCompatible(endpoint);
    };
  }

  private String joinPath(String canonicalEndpoint, String suffix) {
    return canonicalEndpoint.replaceAll("/+$", "") + "/" + suffix.replaceAll("^/+", "");
  }

  private static String endpointHost(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      return null;
    }
    return URI.create(endpoint).getHost();
  }

  private static short keyVersionFromEnvelope(byte[] encryptedEnvelope) {
    return (short) ByteBuffer.wrap(encryptedEnvelope).getInt();
  }

  private static boolean isTimeout(Throwable throwable) {
    Throwable currentThrowable = throwable;
    while (currentThrowable != null) {
      if (currentThrowable instanceof SocketTimeoutException) {
        return true;
      }
      currentThrowable = currentThrowable.getCause();
    }
    return false;
  }

  private record OpenAiModelsResponse(List<OpenAiModel> data) {}

  private record OpenAiModel(String id) {}
}
