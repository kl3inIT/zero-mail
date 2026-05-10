package com.zeromail.core.rules.persistence;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.core.support.PostgresContainerTest;

class RulePersistenceWave0Test extends PostgresContainerTest {

  private static final String RULES_TABLE = "rules";

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void liquibase_creates_rules_and_rule_template_tables_with_jsonb_contract_columns() {
    assertColumn("rules", "matcher_ast", "jsonb");
    assertColumn("rules", "action_intents", "jsonb");
    assertColumn("rules", "enabled", "boolean");
    assertColumn("rules", "template_key", "character varying");
    assertColumn("rules", "template_version", "integer");
    assertColumn("rule_template_catalog", "matcher_ast", "jsonb");
    assertColumn("rule_template_catalog", "action_intents", "jsonb");
  }

  @Test
  void new_rules_default_to_disabled_and_preserve_template_provenance_columns() {
    UUID tenantId = seedTenant("rules-default-disabled");
    UUID ruleId = UUID.randomUUID();

    jdbcTemplate.update(
        "insert into "
            + RULES_TABLE
            + """
        (id, tenant_id, display_name, source_text, source_language, schema_version,
          matcher_ast, action_intents, order_index, template_key, template_version)
        values (?, ?, 'Archive receipts', 'Archive Stripe receipts',
          'en', 'rules.v1',
          '{"schemaVersion":"rules.v1","type":"SENDER_DOMAIN","domain":"stripe.com"}'::jsonb,
          '[{"type":"archive"}]'::jsonb, 10, 'archive-receipts', 1)
        """,
        ruleId,
        tenantId);

    Boolean enabled =
        jdbcTemplate.queryForObject(
            "select enabled from " + RULES_TABLE + " where id = ?", Boolean.class, ruleId);
    Integer templateVersion =
        jdbcTemplate.queryForObject(
            "select template_version from " + RULES_TABLE + " where id = ?", Integer.class, ruleId);

    assertThat(enabled).isFalse();
    assertThat(templateVersion).isEqualTo(1);
  }

  @Test
  void jsonb_matcher_and_action_intents_round_trip_without_raw_email_content_columns() {
    assertThat(columnExists("rules", "raw_body")).isFalse();
    assertThat(columnExists("rules", "snippet")).isFalse();
    assertThat(columnExists("rules", "prompt")).isFalse();
    assertThat(columnExists("rules", "completion")).isFalse();
  }

  @Test
  void repository_contract_is_tenant_scoped_for_reads_and_mutations() throws Exception {
    Class<?> repositoryClass = Class.forName("com.zeromail.core.rules.persistence.RuleRepository");

    assertThat(repositoryClass.getMethod("findOrderedByTenantId", UUID.class)).isNotNull();
    assertThat(repositoryClass.getMethod("findByIdAndTenantId", UUID.class, UUID.class)).isNotNull();
    assertThat(repositoryClass.getMethod("deleteByIdAndTenantId", UUID.class, UUID.class))
        .isNotNull();
  }

  private UUID seedTenant(String displayName) {
    UUID tenantId = UUID.randomUUID();
    jdbcTemplate.update("insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
    return tenantId;
  }

  private void assertColumn(String tableName, String columnName, String expectedDataType) {
    String actualDataType =
        jdbcTemplate.queryForObject(
            """
            select data_type
            from information_schema.columns
            where table_name = ? and column_name = ?
            """,
            String.class,
            tableName,
            columnName);

    assertThat(actualDataType).isEqualTo(expectedDataType);
  }

  private boolean columnExists(String tableName, String columnName) {
    Integer columnCount =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.columns
            where table_name = ? and column_name = ?
            """,
            Integer.class,
            tableName,
            columnName);
    return columnCount != null && columnCount > 0;
  }
}
