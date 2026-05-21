package com.zeromail.core.billing.usecases;

import java.util.UUID;

public record CreditGrantResult(UUID grantId, boolean created) {}
