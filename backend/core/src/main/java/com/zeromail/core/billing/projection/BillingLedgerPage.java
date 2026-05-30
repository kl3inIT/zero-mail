package com.zeromail.core.billing.projection;

import java.util.List;

public record BillingLedgerPage(List<BillingLedgerEntrySnapshot> entries, String nextCursor) {}
