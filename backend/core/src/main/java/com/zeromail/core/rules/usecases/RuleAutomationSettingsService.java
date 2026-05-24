package com.zeromail.core.rules.usecases;

import com.zeromail.core.rules.persistence.RuleAutomationSettingsRepository;
import com.zeromail.core.rules.projection.RuleAutomationSettingsView;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleAutomationSettingsService {

    private final RuleAutomationSettingsRepository ruleAutomationSettingsRepository;

    public RuleAutomationSettingsService(
            RuleAutomationSettingsRepository ruleAutomationSettingsRepository) {
        this.ruleAutomationSettingsRepository =
                Objects.requireNonNull(
                        ruleAutomationSettingsRepository, "ruleAutomationSettingsRepository");
    }

    @Transactional
    public RuleAutomationSettingsView getOrCreate(UUID tenantId) {
        return ruleAutomationSettingsRepository.getOrCreate(tenantId);
    }

    @Transactional(readOnly = true)
    public RuleAutomationSettingsView readOrDefault(UUID tenantId) {
        return ruleAutomationSettingsRepository.readOrDefault(tenantId);
    }

    @Transactional
    public RuleAutomationSettingsView update(UUID tenantId, boolean autoSendRulesEnabled) {
        return ruleAutomationSettingsRepository.update(tenantId, autoSendRulesEnabled);
    }
}
