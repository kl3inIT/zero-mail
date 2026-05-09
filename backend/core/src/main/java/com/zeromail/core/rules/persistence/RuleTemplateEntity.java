package com.zeromail.core.rules.persistence;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.zeromail.core.rules.model.ActionIntentJsonValidator;
import com.zeromail.core.rules.model.RuleAstJsonValidator;
import com.zeromail.core.rules.model.RuleLanguage;
import com.zeromail.core.rules.model.RuleSchemaVersion;
import com.zeromail.core.rules.model.RuleTemplateStatus;
import com.zeromail.core.shared.persistence.AbstractAuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "rule_template_catalog")
@SuppressWarnings("JpaDataSourceORMInspection")
public class RuleTemplateEntity extends AbstractAuditableEntity {

  private static final RuleAstJsonValidator RULE_AST_JSON_VALIDATOR = new RuleAstJsonValidator();
  private static final ActionIntentJsonValidator ACTION_INTENT_JSON_VALIDATOR =
      new ActionIntentJsonValidator();

  @Column(name = "template_key", nullable = false, length = 128)
  private String templateKey;

  @Column(name = "template_version", nullable = false)
  private int templateVersion;

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

  @Column(name = "status", nullable = false, length = 32)
  private String statusId;

  protected RuleTemplateEntity() {
    // Hibernate
  }

  public RuleTemplateEntity(
      UUID templateId,
      String templateKey,
      int templateVersion,
      String displayName,
      String sourceText,
      RuleLanguage sourceLanguage,
      RuleSchemaVersion schemaVersion,
      String matcherAst,
      String actionIntents,
      RuleTemplateStatus status) {
    super(templateId);
    requireText(templateKey, "templateKey");
    requireText(displayName, "displayName");
    requireText(sourceText, "sourceText");
    this.templateKey = templateKey;
    this.templateVersion = templateVersion;
    this.displayName = displayName;
    this.sourceText = sourceText;
    this.sourceLanguageId = sourceLanguage.id();
    this.schemaVersionId = schemaVersion.id();
    RULE_AST_JSON_VALIDATOR.validateMatcherJson(matcherAst);
    ACTION_INTENT_JSON_VALIDATOR.validateActionIntentsJson(actionIntents);
    this.matcherAst = matcherAst;
    this.actionIntents = actionIntents;
    this.statusId = status.id();
  }

  public String getTemplateKey() {
    return templateKey;
  }

  public int getTemplateVersion() {
    return templateVersion;
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
    RULE_AST_JSON_VALIDATOR.validateMatcherJson(matcherAst);
    return matcherAst;
  }

  public String getActionIntents() {
    ACTION_INTENT_JSON_VALIDATOR.validateActionIntentsJson(actionIntents);
    return actionIntents;
  }

  public RuleTemplateStatus getStatus() {
    return RuleTemplateStatus.fromId(statusId);
  }

  private static void requireText(String text, String fieldName) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
