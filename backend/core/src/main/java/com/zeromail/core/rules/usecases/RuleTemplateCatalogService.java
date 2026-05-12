package com.zeromail.core.rules.usecases;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.rules.domain.RuleTemplateStatus;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.rules.persistence.RuleTemplateEntity;
import com.zeromail.core.rules.persistence.RuleTemplateRepository;
import com.zeromail.core.rules.projection.RuleTemplateProjection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RuleTemplateCatalogService {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final List<RuleTemplateStatus> ACTIVE_TEMPLATE_STATUSES =
            List.of(RuleTemplateStatus.MATERIALIZABLE, RuleTemplateStatus.GALLERY_ONLY);

    private final RuleTemplateRepository ruleTemplateRepository;
    private final RuleRepository ruleRepository;

    public RuleTemplateCatalogService(
            RuleTemplateRepository ruleTemplateRepository, RuleRepository ruleRepository) {
        this.ruleTemplateRepository = ruleTemplateRepository;
        this.ruleRepository = ruleRepository;
    }

    /**
     * Lists the active DB-backed starter template catalog.
     *
     * <p>Version pinning: materialized tenant rules keep the catalog version used at creation time.
     * Later active template versions remain visible in the gallery but do not overwrite or migrate
     * existing tenant rules in Phase 3.
     */
    @Transactional(readOnly = true)
    public List<RuleTemplateProjection> listActiveTemplates(UUID tenantId) {
        List<RuleTemplateEntity> orderedTemplates =
                ruleTemplateRepository
                        .findByStatusIdsOrderByTemplateKeyAscTemplateVersionDesc(activeStatusIds())
                        .stream()
                        .sorted(
                                Comparator.comparingInt(
                                                RuleTemplateCatalogService::statusSortWeight)
                                        .thenComparing(RuleTemplateEntity::getTemplateKey)
                                        .thenComparing(
                                                Comparator.comparingInt(
                                                                RuleTemplateEntity
                                                                        ::getTemplateVersion)
                                                        .reversed()))
                        .toList();
        // Batch the per-template materialization lookup into a single query
        // keyed by templateKey. Avoids N+1 SELECTs as the catalog grows.
        List<String> templateKeys =
                orderedTemplates.stream()
                        .map(RuleTemplateEntity::getTemplateKey)
                        .distinct()
                        .toList();
        Map<String, RuleEntity> materializedRulesByTemplateKey = new LinkedHashMap<>();
        if (!templateKeys.isEmpty()) {
            for (RuleEntity materializedRule :
                    ruleRepository.findByTenantIdAndTemplateKeyIn(tenantId, templateKeys)) {
                // RuleEntity.templateKey is unique per (tenantId, templateKey)
                // by the schema constraint, so putIfAbsent is defensive only.
                materializedRulesByTemplateKey.putIfAbsent(
                        materializedRule.getTemplateKey(), materializedRule);
            }
        }
        return orderedTemplates.stream()
                .map(
                        ruleTemplateEntity ->
                                toView(
                                        ruleTemplateEntity,
                                        Optional.ofNullable(
                                                materializedRulesByTemplateKey.get(
                                                        ruleTemplateEntity.getTemplateKey()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<RuleTemplateEntity> resolveTemplate(
            String templateKey, Integer templateVersion) {
        if (templateVersion != null) {
            return ruleTemplateRepository
                    .findByTemplateKeyAndTemplateVersion(templateKey, templateVersion)
                    .filter(
                            ruleTemplateEntity ->
                                    ACTIVE_TEMPLATE_STATUSES.contains(
                                            ruleTemplateEntity.getStatus()));
        }
        return ruleTemplateRepository
                .findByTemplateKeyAndStatusIdsOrderByTemplateVersionDesc(
                        templateKey, activeStatusIds())
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<RuleTemplateEntity> resolveLatestMaterializableTemplate(String templateKey) {
        return ruleTemplateRepository
                .findByTemplateKeyAndStatusIdsOrderByTemplateVersionDesc(
                        templateKey, List.of(RuleTemplateStatus.MATERIALIZABLE.id()))
                .stream()
                .findFirst();
    }

    private RuleTemplateProjection toView(
            RuleTemplateEntity ruleTemplateEntity, Optional<RuleEntity> materializedRule) {
        return new RuleTemplateProjection(
                ruleTemplateEntity.getTemplateKey(),
                ruleTemplateEntity.getTemplateVersion(),
                ruleTemplateEntity.getDisplayName(),
                localizedCopyKey(ruleTemplateEntity.getTemplateKey()),
                ruleTemplateEntity.getSourceText(),
                summarizeActionIntents(ruleTemplateEntity.getActionIntents()),
                ruleTemplateEntity.getStatus(),
                ruleTemplateEntity.getStatus() == RuleTemplateStatus.MATERIALIZABLE,
                materializedRule.isPresent(),
                materializedRule.map(RuleEntity::isCustomized).orElse(false));
    }

    private static String localizedCopyKey(String templateKey) {
        return "rules.templates." + templateKey.replace('-', '.') + ".sourceText";
    }

    private static List<String> activeStatusIds() {
        return ACTIVE_TEMPLATE_STATUSES.stream().map(RuleTemplateStatus::id).toList();
    }

    private static int statusSortWeight(RuleTemplateEntity ruleTemplateEntity) {
        return switch (ruleTemplateEntity.getStatus()) {
            case MATERIALIZABLE -> 0;
            case GALLERY_ONLY -> 1;
            case DEPRECATED -> 2;
        };
    }

    private static String summarizeActionIntents(String actionIntentsJson) {
        try {
            JsonNode actionIntentRoot = OBJECT_MAPPER.readTree(actionIntentsJson);
            List<String> actionSummaries = new ArrayList<>();
            for (JsonNode actionIntentNode : actionIntentRoot) {
                RuleActionType actionType = RuleActionType.fromId(actionType(actionIntentNode));
                actionSummaries.add(actionSummary(actionType, actionIntentNode));
            }
            return String.join(", ", actionSummaries);
        } catch (JacksonException jacksonFailure) {
            throw new IllegalArgumentException(
                    "Template action intents must be valid JSON", jacksonFailure);
        }
    }

    private static String actionType(JsonNode actionIntentNode) {
        JsonNode typeNode = actionIntentNode.path("type");
        if (typeNode.isMissingNode() || typeNode.isNull() || !typeNode.isString()) {
            throw new IllegalArgumentException("Template action type is required");
        }
        return typeNode.asString();
    }

    private static String actionSummary(RuleActionType actionType, JsonNode actionIntentNode) {
        return switch (actionType) {
            case LABEL -> "label " + optionalText(actionIntentNode, "labelName").orElse("message");
            case ARCHIVE -> "archive";
            case SAVE_DRAFT -> "save draft";
        };
    }

    private static Optional<String> optionalText(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull() || !fieldNode.isString()) {
            return Optional.empty();
        }
        String text = fieldNode.asString();
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.trim());
    }
}
