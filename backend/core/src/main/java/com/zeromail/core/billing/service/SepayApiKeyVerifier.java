package com.zeromail.core.billing.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.zeromail.core.config.ZeroMailCoreProperties;

/** Constant-time API-key comparator for the SePay webhook auth header. */
@Component
public class SepayApiKeyVerifier {

  private static final String AUTHORIZATION_PREFIX = "Apikey ";

  private final byte[] expectedKeyBytes;

  @Autowired
  public SepayApiKeyVerifier(ZeroMailCoreProperties properties) {
    this(properties.billing().sepay().webhookApiKey());
  }

  SepayApiKeyVerifier(String expectedApiKey) {
    expectedKeyBytes = expectedApiKey.getBytes(StandardCharsets.UTF_8);
  }

  public boolean verify(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith(AUTHORIZATION_PREFIX)) {
      return false;
    }
    byte[] providedKeyBytes =
        authorizationHeader
            .substring(AUTHORIZATION_PREFIX.length())
            .getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expectedKeyBytes, providedKeyBytes);
  }
}
