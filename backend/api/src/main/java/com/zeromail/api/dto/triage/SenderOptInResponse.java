package com.zeromail.api.dto.triage;

public record SenderOptInResponse(String senderEmail, boolean optedIn) {

  public static SenderOptInResponse from(String senderEmail, boolean optedIn) {
    return new SenderOptInResponse(senderEmail, optedIn);
  }
}
