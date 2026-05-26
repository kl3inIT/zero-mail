package com.zeromail.core.triage.usecases;

import java.util.UUID;

@FunctionalInterface
public interface TriageDraftSettings {

    boolean autoDraftRepliesEnabled(UUID tenantId);
}
