package com.zeromail.core.gmail.persistence;

import java.io.Serializable;
import java.util.UUID;

public record MailMessageObservedId(UUID tenantId, String gmailMessageId) implements Serializable {}
