package com.zeromail.core.rules.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.type.SqlTypes;

import com.zeromail.core.rules.model.ActionIntentJsonValidator;
import com.zeromail.core.rules.model.RuleAstJsonValidator;
import com.zeromail.core.rules.model.RuleLanguage;
import com.zeromail.core.rules.model.RuleSchemaVersion;
import com.zeromail.core.rules.model.RuleStatusView;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "rules")
@SuppressWarnings("JpaDataSourceORMInspection")
public class RuleEntity extends AbstractTenantOwnedEntity {

  private static final RuleAstJsonValidator RULE_AST_JSON_VALIDATOR = new RuleAstJsonValidator();
  private static final ActionIntentJsonValidator ACTION_INTENT_JSON_VALIDATOR =
      new ActionIntentJsonValidator();

  @Column(name = "display_name", nullable = false, length = 160)
  private String displayName;

  @Column(name = "source_text", nullable = false)
  private String sourceText;

  @Column(name = "source_language", nullable = false, length = 16)
  private String sourceLanguageId;

  @Column(name = "schema_version", nullable = false, length = 32)
  private String schemaVersionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "matcher_ast", columnDefinition = "jsonb", nullable = false)
  private String matcherAst;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "action_intents", columnDefinition = "jsonb", nullable = false)
  private String actionIntents;

  @Column(name = "enabled", nullable = false)
  @OptimisticLock(excluded = true)
  private boolean enabled = false;

  @Column(name = "order_index", nullable = false)
  private int orderIndex;

  @Column(name = "last_previewed_entity_version")
  @OptimisticLock(excluded = true)
  private Integer lastPreviewedEntityVersion;

  @Column(name = "last_previewed_at")
  @OptimisticLock(excluded = true)
  private Instant lastPreviewedAt;

  @Column(name = "template_key", length = 128)
  private String templateKey;

  @Column(name = "template_version")
  private Integer templateVersion;

  @Column(name = "customized", nullable = false)
  private boolean customized = false;

  protected RuleEntity() {
    // Hibernate
  }

  public RuleEntity(
      UUID ruleId,
      UUID tenantId,
      String displayName,
      String sourceText,
      RuleLanguage sourceLanguage,
      RuleSchemaVersion schemaVersion,
      String matcherAst,
      String actionIntents,
      int orderIndex,
      String templateKey,
      Integer templateVersion) {
    super(ruleId, tenantId);
    replaceDefinition(displayName, sourceText, sourceLanguage, schemaVersion, matcherAst, actionIntents);
    this.orderIndex = orderIndex;
    this.templateKey = templateKey;
    this.templateVersion = templateVersion;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getSourceText() {
    return sourceText;
  }

  public RuleLanguage getSourceLanguage() {
    return RuleLanguage.fromId(sourceLanguageId);
  }

  public RuleSchemaVersion getSchemaVersion() {
    return RuleSchemaVersion.fromId(schemaVersionId);
  }

  public String getMatcherAst() {
    validateMatcherAst(matcherAst);
    return matcherAst;
  }

  public String getActionIntents() {
    validateActionIntents(actionIntents);
    return actionIntents;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getOrderIndex() {
    return orderIndex;
  }

  public Integer getLastPreviewedEntityVersion() {
    return lastPreviewedEntityVersion;
  }

  public Instant getLastPreviewedAt() {
    return lastPreviewedAt;
  }

  public String getTemplateKey() {
    return templateKey;
  }

  public Integer getTemplateVersion() {
    return templateVersion;
  }

  public boolean isCustomized() {
    return customized;
  }

  public Integer getEntityVersion() {
    return getVersion();
  }

  public RuleStatusView toStatusView() {
    return new RuleStatusView(
        new com.zeromail.core.rules.model.RuleId(getId()),
        displayName,
        sourceText,
        enabled,
        orderIndex,
        getSourceLanguage(),
        getSchemaVersion(),
        getMatcherAst(),
        getActionIntents(),
        getEntityVersion(),
        lastPreviewedEntityVersion,
        lastPreviewedAt,
        templateKey,
        templateVersion,
        customized);
  }

  public void replaceDefinition(
      String displayName,
      String sourceText,
      RuleLanguage sourceLanguage,
      RuleSchemaVersion schemaVersion,
      String matcherAst,
      String actionIntents) {
    requireText(displayName, "displayName");
    requireText(sourceText, "sourceText");
    this.displayName = displayName;
    this.sourceText = sourceText;
    this.sourceLanguageId = sourceLanguage.id();
    this.schemaVersionId = schemaVersion.id();
    validateMatcherAst(matcherAst);
    validateActionIntents(actionIntents);
    this.matcherAst = matcherAst;
    this.actionIntents = actionIntents;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setOrderIndex(int orderIndex) {
    this.orderIndex = orderIndex;
  }

  public void markPreviewed(Integer previewedEntityVersion, Instant previewedAt) {
    this.lastPreviewedEntityVersion = previewedEntityVersion;
    this.lastPreviewedAt = previewedAt;
  }

  public void clearPreview() {
    this.lastPreviewedEntityVersion = null;
    this.lastPreviewedAt = null;
  }

  public void markCustomized() {
    this.customized = true;
  }

  private static void validateMatcherAst(String matcherAst) {
    RULE_AST_JSON_VALIDATOR.validateMatcherJson(matcherAst);
  }

  private static void validateActionIntents(String actionIntents) {
    ACTION_INTENT_JSON_VALIDATOR.validateActionIntentsJson(actionIntents);
  }

  private static void requireText(String text, String fieldName) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
