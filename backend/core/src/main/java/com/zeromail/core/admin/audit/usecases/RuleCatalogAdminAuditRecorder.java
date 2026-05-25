package com.zeromail.core.admin.audit.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.rules.catalog.usecases.RuleCatalogAuditAction;
import com.zeromail.core.rules.catalog.usecases.RuleCatalogAuditEvent;
import com.zeromail.core.rules.catalog.usecases.RuleCatalogAuditRecorder;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RuleCatalogAdminAuditRecorder implements RuleCatalogAuditRecorder {

    private final AdminAuditWriter adminAuditWriter;

    public RuleCatalogAdminAuditRecorder(AdminAuditWriter adminAuditWriter) {
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
    }

    @Override
    public void record(RuleCatalogAuditEvent event) {
        RuleCatalogAuditEvent auditEvent = Objects.requireNonNull(event, "event must not be null");
        adminAuditWriter.append(
                toAdminAuditAction(auditEvent.action()),
                auditEvent.targetKind(),
                auditEvent.targetId(),
                auditEvent.beforeState(),
                auditEvent.afterState(),
                auditEvent.reason(),
                auditEvent.requestIp(),
                auditEvent.requestId());
    }

    private static AdminAuditAction toAdminAuditAction(RuleCatalogAuditAction action) {
        return switch (action) {
            case RULE_CATALOG_PERSONA_CREATED -> AdminAuditAction.RULE_CATALOG_PERSONA_CREATED;
            case RULE_CATALOG_PERSONA_UPDATED -> AdminAuditAction.RULE_CATALOG_PERSONA_UPDATED;
            case RULE_CATALOG_PERSONA_ENABLED -> AdminAuditAction.RULE_CATALOG_PERSONA_ENABLED;
            case RULE_CATALOG_PERSONA_DISABLED -> AdminAuditAction.RULE_CATALOG_PERSONA_DISABLED;
            case RULE_CATALOG_PERSONAS_REORDERED ->
                    AdminAuditAction.RULE_CATALOG_PERSONAS_REORDERED;
            case RULE_CATALOG_PROMPT_CREATED -> AdminAuditAction.RULE_CATALOG_PROMPT_CREATED;
            case RULE_CATALOG_PROMPT_UPDATED -> AdminAuditAction.RULE_CATALOG_PROMPT_UPDATED;
            case RULE_CATALOG_PROMPT_ENABLED -> AdminAuditAction.RULE_CATALOG_PROMPT_ENABLED;
            case RULE_CATALOG_PROMPT_DISABLED -> AdminAuditAction.RULE_CATALOG_PROMPT_DISABLED;
            case RULE_CATALOG_PROMPTS_REORDERED -> AdminAuditAction.RULE_CATALOG_PROMPTS_REORDERED;
            case RULE_CATALOG_ACTION_DESCRIPTOR_UPSERTED ->
                    AdminAuditAction.RULE_CATALOG_ACTION_DESCRIPTOR_UPSERTED;
            case RULE_CATALOG_ACTION_DESCRIPTOR_ENABLED ->
                    AdminAuditAction.RULE_CATALOG_ACTION_DESCRIPTOR_ENABLED;
            case RULE_CATALOG_ACTION_DESCRIPTOR_DISABLED ->
                    AdminAuditAction.RULE_CATALOG_ACTION_DESCRIPTOR_DISABLED;
            case RULE_CATALOG_ACTION_DESCRIPTORS_REORDERED ->
                    AdminAuditAction.RULE_CATALOG_ACTION_DESCRIPTORS_REORDERED;
        };
    }
}
