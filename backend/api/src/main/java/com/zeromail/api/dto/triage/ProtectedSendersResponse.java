package com.zeromail.api.dto.triage;

import java.util.List;

import com.zeromail.core.triage.application.ProtectedSenderListItem;

public record ProtectedSendersResponse(List<ProtectedSenderResponse> senders) {

  public static ProtectedSendersResponse from(List<ProtectedSenderListItem> protectedSenders) {
    return new ProtectedSendersResponse(
        protectedSenders.stream().map(ProtectedSenderResponse::from).toList());
  }

  public record ProtectedSenderResponse(String senderEmail, boolean optedIn) {

    private static ProtectedSenderResponse from(ProtectedSenderListItem protectedSender) {
      return new ProtectedSenderResponse(protectedSender.senderEmail(), protectedSender.optedIn());
    }
  }
}
