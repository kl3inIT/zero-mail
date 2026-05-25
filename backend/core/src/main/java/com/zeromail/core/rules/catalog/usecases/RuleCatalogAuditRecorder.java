package com.zeromail.core.rules.catalog.usecases;

public interface RuleCatalogAuditRecorder {

    void record(RuleCatalogAuditEvent event);
}
