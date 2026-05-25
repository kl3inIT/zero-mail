package com.zeromail.core.rules.catalog.usecases;

import com.zeromail.core.rules.catalog.domain.RuleCatalogLocale;
import com.zeromail.core.rules.catalog.persistence.RuleCatalogRepository;
import com.zeromail.core.rules.catalog.projection.RuleActionDescriptorView;
import com.zeromail.core.rules.catalog.projection.RuleExamplePersonaView;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleCatalogUserService {

    private final RuleCatalogRepository ruleCatalogRepository;

    public RuleCatalogUserService(RuleCatalogRepository ruleCatalogRepository) {
        this.ruleCatalogRepository =
                Objects.requireNonNull(ruleCatalogRepository, "ruleCatalogRepository");
    }

    @Transactional(readOnly = true)
    public List<RuleExamplePersonaView> listExamplePersonas(RuleCatalogLocale locale) {
        return ruleCatalogRepository.findEnabledPersonasWithPrompts(locale);
    }

    @Transactional(readOnly = true)
    public List<RuleActionDescriptorView> listActionDescriptors(RuleCatalogLocale locale) {
        return ruleCatalogRepository.findEnabledActionDescriptors(locale);
    }
}
