package com.zeromail.core.triage.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class SenderEmailCanonicalizer {

  private static final Pattern PLAUSIBLE_EMAIL_PATTERN =
      Pattern.compile("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,}$");

  public String canonicalize(String rawSenderEmail) {
    if (rawSenderEmail == null || rawSenderEmail.isBlank()) {
      throw new IllegalArgumentException("senderEmail must not be blank");
    }
    String extractedAddress = extractAddress(rawSenderEmail.trim());
    String canonicalizedEmail = extractedAddress.toLowerCase(Locale.ROOT).trim();
    if (!PLAUSIBLE_EMAIL_PATTERN.matcher(canonicalizedEmail).matches()) {
      throw new IllegalArgumentException("senderEmail is malformed");
    }
    return canonicalizedEmail;
  }

  public String redisCacheKeyComponent(String canonicalizedEmail) {
    return HexFormat.of().formatHex(sha256(canonicalize(canonicalizedEmail)));
  }

  public String gmailSearchToken(String canonicalizedEmail) {
    String safeAddress = canonicalize(canonicalizedEmail)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"");
    return "\"" + safeAddress + "\"";
  }

  private static String extractAddress(String senderEmail) {
    int leftAngle = senderEmail.lastIndexOf('<');
    int rightAngle = senderEmail.lastIndexOf('>');
    if (leftAngle >= 0 || rightAngle >= 0) {
      if (leftAngle < 0 || rightAngle <= leftAngle) {
        throw new IllegalArgumentException("senderEmail is malformed");
      }
      return senderEmail.substring(leftAngle + 1, rightAngle).trim();
    }
    return senderEmail;
  }

  private static byte[] sha256(String value) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      return messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
      throw new IllegalStateException("SHA-256 digest is unavailable", noSuchAlgorithmException);
    }
  }
}
