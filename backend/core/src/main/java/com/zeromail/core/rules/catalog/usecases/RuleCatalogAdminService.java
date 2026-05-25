package com.zeromail.core.rules.catalog.usecases;

import com.zeromail.core.rules.catalog.persistence.RuleCatalogRepository;
import com.zeromail.core.rules.catalog.projection.RuleActionDescriptorAdminView;
import com.zeromail.core.rules.catalog.projection.RuleExamplePersonaAdminView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleCatalogAdminService {

    private final RuleCatalogRepository ruleCatalogRepository;
    private final RuleCatalogAuditRecorder ruleCatalogAuditRecorder;

    public RuleCatalogAdminService(
            RuleCatalogRepository ruleCatalogRepository,
            RuleCatalogAuditRecorder ruleCatalogAuditRecorder) {
        this.ruleCatalogRepository =
                Objects.requireNonNull(ruleCatalogRepository, "ruleCatalogRepository");
        this.ruleCatalogAuditRecorder =
                Objects.requireNonNull(ruleCatalogAuditRecorder, "ruleCatalogAuditRecorder");
    }

    @Transactional(readOnly = true)
    public List<RuleExamplePersonaAdminView> listPersonas() {
        return ruleCatalogRepository.findPersonasForAdmin();
    }

    @Transactional(readOnly = true)
    public List<RuleActionDescriptorAdminView> listActionDescriptors() {
        return ruleCatalogRepository.findActionDescriptorsForAdmin();
    }

    @Transactional
    public UUID createPersona(RulePersonaWriteCommand command, String requestIp, UUID requestId) {
        UUID personaId = ruleCatalogRepository.insertPersona(command);
        Map<String, ?> afterState = ruleCatalogRepository.findPersonaState(personaId);
        recordChange(
                RuleCatalogAuditAction.RULE_CATALOG_PERSONA_CREATED,
                RuleCatalogTargetKinds.PERSONA,
                personaId,
                null,
                afterState,
                "create persona",
                requestIp,
                requestId);
        return personaId;
    }

    @Transactional
    public void updatePersona(
            UUID personaId,
            RulePersonaWriteCommand command,
            String reason,
            String requestIp,
            UUID requestId) {
        Map<String, ?> beforeState = ruleCatalogRepository.findPersonaState(personaId);
        ruleCatalogRepository.updatePersona(personaId, command);
        Map<String, ?> afterState = ruleCatalogRepository.findPersonaState(personaId);
        recordChange(
                RuleCatalogAuditAction.RULE_CATALOG_PERSONA_UPDATED,
                RuleCatalogTargetKinds.PERSONA,
                personaId,
                beforeState,
                afterState,
                reason,
                requestIp,
                requestId);
    }

    @Transactional
    public void setPersonaEnabled(
            UUID personaId, boolean enabled, String reason, String requestIp, UUID requestId) {
        Map<String, ?> beforeState = ruleCatalogRepository.findPersonaState(personaId);
        ruleCatalogRepository.setPersonaEnabled(personaId, enabled);
        Map<String, ?> afterState = ruleCatalogRepository.findPersonaState(personaId);
        recordChange(
                enabled
                        ? RuleCatalogAuditAction.RULE_CATALOG_PERSONA_ENABLED
                        : RuleCatalogAuditAction.RULE_CATALOG_PERSONA_DISABLED,
                RuleCatalogTargetKinds.PERSONA,
                personaId,
                beforeState,
                afterState,
                reason,
                requestIp,
                requestId);
    }

    @Transactional
    public void reorderPersonas(
            List<RuleCatalogOrderEntry> orderEntries,
            String reason,
            String requestIp,
            UUID requestId) {
        List<Map<String, Object>> beforeState = ruleCatalogRepository.findPersonaOrderState();
        ruleCatalogRepository.reorderPersonas(orderEntries);
        List<Map<String, Object>> afterState = ruleCatalogRepository.findPersonaOrderState();
        recordChange(
                RuleCatalogAuditAction.RULE_CATALOG_PERSONAS_REORDERED,
                RuleCatalogTargetKinds.PERSONA,
                null,
                Map.of("order", beforeState),
                Map.of("order", afterState),
                reason,
                requestIp,
                requestId);
    }

    @Transactional
    public UUID createPrompt(
            UUID personaId,
            RulePromptWriteCommand command,
            String reason,
            String requestIp,
            UUID requestId) {
        UUID promptId = ruleCatalogRepository.insertPrompt(personaId, command);
        Map<String, ?> afterState = ruleCatalogRepository.findPromptState(promptId);
        recordChange(
                RuleCatalogAuditAction.RULE_CATALOG_PROMPT_CREATED,
                RuleCatalogTargetKinds.PROMPT,
                promptId,
                null,
                afterState,
                reason,
                requestIp,
                requestId);
        return promptId;
    }

    @Transactional
    public void updatePrompt(
            UUID promptId,
            RulePromptWriteCommand command,
            String reason,
            String requestIp,
            UUID requestId) {
        Map<String, ?> beforeState = ruleCatalogRepository.findPromptState(promptId);
        ruleCatalogRepository.updatePrompt(promptId, command);
        Map<String, ?> afterState = ruleCatalogRepository.findPromptState(promptId);
        recordChange(
                RuleCatalogAuditAction.RULE_CATALOG_PROMPT_UPDATED,
                RuleCatalogTargetKinds.PROMPT,
                promptId,
                beforeState,
                afterState,
                reason,
                requestIp,
                requestId);
    }

    @Transactional
    public void setPromptEnabled(
            UUID promptId, boolean enabled, String reason, String requestIp, UUID requestId) {
        Map<String, ?> beforeState = ruleCatalogRepository.findPromptState(promptId);
        ruleCatalogRepository.setPromptEnabled(promptId, enabled);
        Map<String, ?> afterState = ruleCatalogRepository.findPromptState(promptId);
        recordChange(
                enabled
                        ? RuleCatalogAuditAction.RULE_CATALOG_PROMPT_ENABLED
                        : RuleCatalogAuditAction.RULE_CATALOG_PROMPT_DISABLED,
                RuleCatalogTargetKinds.PROMPT,
                promptId,
                beforeState,
                afterState,
                reason,
                requestIp,
                requestId);
    }

    @Transactional
    public void reorderPrompts(
            UUID personaId,
            List<RuleCatalogOrderEntry> orderEntries,
            String reason,
            String requestIp,
            UUID requestId) {
        List<Map<String, Object>> beforeState =
                ruleCatalogRepository.findPromptOrderState(personaId);
        ruleCatalogRepository.reorderPrompts(personaId, orderEntries);
        List<Map<String, Object>> afterState =
                ruleCatalogRepository.findPromptOrderState(personaId);
        recordChange(
                RuleCatalogAuditAction.RULE_CATALOG_PROMPTS_REORDERED,
                RuleCatalogTargetKinds.PROMPT,
                personaId,
                Map.of("order", beforeState),
                Map.of("order", afterState),
                reason,
                requestIp,
                requestId);
    }

    @Transactional
    public void upsertActionDescriptor(
            RuleActionDescriptorWriteCommand command,
            String reason,
            String requestIp,
            UUID requestId) {
        Map<String, ?> beforeState =
                ruleCatalogRepository.findActionDescriptorStateOrEmpty(command.actionKey());
        ruleCatalogRepository.upsertActionDescriptor(command);
        Map<String, ?> afterState =
                ruleCatalogRepository.findActionDescriptorState(command.actionKey());
        recordChange(
                RuleCatalogAuditAction.RULE_CATALOG_ACTION_DESCRIPTOR_UPSERTED,
                RuleCatalogTargetKinds.ACTION_DESCRIPTOR,
                null,
                beforeState.isEmpty() ? null : beforeState,
                afterState,
                reason,
                requestIp,
                requestId);
    }

    @Transactional
    public void setActionDescriptorEnabled(
            String actionKey, boolean enabled, String reason, String requestIp, UUID requestId) {
        String catalogActionKey = RuleCatalogCommandText.requireText(actionKey, "actionKey");
        Map<String, ?> beforeState =
                ruleCatalogRepository.findActionDescriptorState(catalogActionKey);
        ruleCatalogRepository.setActionDescriptorEnabled(catalogActionKey, enabled);
        Map<String, ?> afterState =
                ruleCatalogRepository.findActionDescriptorState(catalogActionKey);
        recordChange(
                enabled
                        ? RuleCatalogAuditAction.RULE_CATALOG_ACTION_DESCRIPTOR_ENABLED
                        : RuleCatalogAuditAction.RULE_CATALOG_ACTION_DESCRIPTOR_DISABLED,
                RuleCatalogTargetKinds.ACTION_DESCRIPTOR,
                null,
                beforeState,
                afterState,
                reason,
                requestIp,
                requestId);
    }

    @Transactional
    public void reorderActionDescriptors(
            List<RuleActionDescriptorOrderEntry> orderEntries,
            String reason,
            String requestIp,
            UUID requestId) {
        List<Map<String, Object>> beforeState =
                ruleCatalogRepository.findActionDescriptorOrderState();
        ruleCatalogRepository.reorderActionDescriptors(orderEntries);
        List<Map<String, Object>> afterState =
                ruleCatalogRepository.findActionDescriptorOrderState();
        recordChange(
                RuleCatalogAuditAction.RULE_CATALOG_ACTION_DESCRIPTORS_REORDERED,
                RuleCatalogTargetKinds.ACTION_DESCRIPTOR,
                null,
                Map.of("order", beforeState),
                Map.of("order", afterState),
                reason,
                requestIp,
                requestId);
    }

    private void recordChange(
            RuleCatalogAuditAction action,
            String targetKind,
            UUID targetId,
            Map<String, ?> beforeState,
            Map<String, ?> afterState,
            String reason,
            String requestIp,
            UUID requestId) {
        ruleCatalogAuditRecorder.record(
                new RuleCatalogAuditEvent(
                        action,
                        targetKind,
                        targetId,
                        beforeState,
                        afterState,
                        RuleCatalogCommandText.requireText(reason, "reason"),
                        requestIp,
                        requestId));
    }
}
