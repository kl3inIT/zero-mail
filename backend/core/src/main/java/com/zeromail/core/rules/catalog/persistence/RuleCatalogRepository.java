package com.zeromail.core.rules.catalog.persistence;

import com.zeromail.core.rules.catalog.domain.RuleCatalogLocale;
import com.zeromail.core.rules.catalog.projection.RuleActionDescriptorAdminView;
import com.zeromail.core.rules.catalog.projection.RuleActionDescriptorView;
import com.zeromail.core.rules.catalog.projection.RuleExamplePersonaAdminView;
import com.zeromail.core.rules.catalog.projection.RuleExamplePersonaView;
import com.zeromail.core.rules.catalog.projection.RuleExamplePromptAdminView;
import com.zeromail.core.rules.catalog.projection.RuleExamplePromptView;
import com.zeromail.core.rules.catalog.usecases.RuleActionDescriptorOrderEntry;
import com.zeromail.core.rules.catalog.usecases.RuleActionDescriptorWriteCommand;
import com.zeromail.core.rules.catalog.usecases.RuleCatalogOrderEntry;
import com.zeromail.core.rules.catalog.usecases.RulePersonaWriteCommand;
import com.zeromail.core.rules.catalog.usecases.RulePromptWriteCommand;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class RuleCatalogRepository {

    private static final int REORDER_OFFSET = 1_000_000;

    private final JdbcTemplate jdbcTemplate;

    public RuleCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public List<RuleExamplePersonaView> findEnabledPersonasWithPrompts(RuleCatalogLocale locale) {
        Objects.requireNonNull(locale, "locale must not be null");
        LinkedHashMap<UUID, PersonaViewAccumulator> personasById = new LinkedHashMap<>();
        RowCallbackHandler personaViewRowCallbackHandler =
                resultSet -> addPersonaViewRow(personasById, resultSet);
        jdbcTemplate.query(
                """
                SELECT persona.persona_id,
                       persona.persona_key,
                       CASE
                         WHEN ? = 'vi' THEN COALESCE(NULLIF(BTRIM(persona.display_name_vi), ''), persona.display_name_en)
                         ELSE persona.display_name_en
                       END AS display_name,
                       persona.icon,
                       persona.display_order AS persona_display_order,
                       prompt.prompt_id,
                       CASE
                         WHEN ? = 'vi' THEN COALESCE(NULLIF(BTRIM(prompt.prompt_vi), ''), prompt.prompt_en)
                         ELSE prompt.prompt_en
                       END AS prompt_text,
                       prompt.display_order AS prompt_display_order
                FROM rule_example_persona persona
                JOIN rule_example_prompt prompt ON prompt.persona_id = persona.persona_id
                WHERE persona.enabled = TRUE
                  AND prompt.enabled = TRUE
                ORDER BY persona.display_order, prompt.display_order
                """,
                personaViewRowCallbackHandler,
                locale.id(),
                locale.id());
        return personasById.values().stream().map(PersonaViewAccumulator::toView).toList();
    }

    public List<RuleActionDescriptorView> findEnabledActionDescriptors(RuleCatalogLocale locale) {
        Objects.requireNonNull(locale, "locale must not be null");
        return jdbcTemplate.query(
                """
                SELECT action_key,
                       CASE
                         WHEN ? = 'vi' THEN COALESCE(NULLIF(BTRIM(label_vi), ''), label_en)
                         ELSE label_en
                       END AS label,
                       CASE
                         WHEN ? = 'vi' THEN COALESCE(NULLIF(BTRIM(description_vi), ''), description_en)
                         ELSE description_en
                       END AS description,
                       risk_level,
                       availability_status,
                       display_order
                FROM rule_action_descriptor
                WHERE enabled = TRUE
                ORDER BY display_order
                """,
                RuleCatalogRepository::mapActionDescriptorView,
                locale.id(),
                locale.id());
    }

    public List<RuleExamplePersonaAdminView> findPersonasForAdmin() {
        LinkedHashMap<UUID, PersonaAdminAccumulator> personasById = new LinkedHashMap<>();
        RowCallbackHandler personaAdminRowCallbackHandler =
                resultSet -> addPersonaAdminRow(personasById, resultSet);
        jdbcTemplate.query(
                """
                SELECT persona.persona_id,
                       persona.persona_key,
                       persona.display_name_en,
                       persona.display_name_vi,
                       persona.icon,
                       persona.display_order AS persona_display_order,
                       persona.enabled AS persona_enabled,
                       prompt.prompt_id,
                       prompt.prompt_en,
                       prompt.prompt_vi,
                       prompt.display_order AS prompt_display_order,
                       prompt.enabled AS prompt_enabled
                FROM rule_example_persona persona
                LEFT JOIN rule_example_prompt prompt ON prompt.persona_id = persona.persona_id
                ORDER BY persona.display_order, prompt.display_order NULLS LAST
                """,
                personaAdminRowCallbackHandler);
        return personasById.values().stream().map(PersonaAdminAccumulator::toView).toList();
    }

    public List<RuleActionDescriptorAdminView> findActionDescriptorsForAdmin() {
        return jdbcTemplate.query(
                """
                SELECT action_key,
                       label_en,
                       label_vi,
                       description_en,
                       description_vi,
                       risk_level,
                       availability_status,
                       display_order,
                       enabled
                FROM rule_action_descriptor
                ORDER BY display_order
                """,
                RuleCatalogRepository::mapActionDescriptorAdminView);
    }

    public UUID insertPersona(RulePersonaWriteCommand command) {
        UUID personaId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO rule_example_persona(
                            persona_key, display_name_en, display_name_vi, icon, display_order,
                            enabled, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, NOW())
                        RETURNING persona_id
                        """,
                        UUID.class,
                        command.personaKey(),
                        command.displayNameEn(),
                        command.displayNameVi(),
                        command.icon(),
                        command.displayOrder(),
                        command.enabled());
        return Objects.requireNonNull(personaId, "inserted personaId must not be null");
    }

    public void updatePersona(UUID personaId, RulePersonaWriteCommand command) {
        int updatedRows =
                jdbcTemplate.update(
                        """
                        UPDATE rule_example_persona
                        SET persona_key = ?,
                            display_name_en = ?,
                            display_name_vi = ?,
                            icon = ?,
                            display_order = ?,
                            enabled = ?,
                            updated_at = NOW(),
                            version = version + 1
                        WHERE persona_id = ?
                        """,
                        command.personaKey(),
                        command.displayNameEn(),
                        command.displayNameVi(),
                        command.icon(),
                        command.displayOrder(),
                        command.enabled(),
                        personaId);
        requireUpdated(updatedRows, "rule_example_persona", personaId);
    }

    public void setPersonaEnabled(UUID personaId, boolean enabled) {
        int updatedRows =
                jdbcTemplate.update(
                        """
                        UPDATE rule_example_persona
                        SET enabled = ?,
                            updated_at = NOW(),
                            version = version + 1
                        WHERE persona_id = ?
                        """,
                        enabled,
                        personaId);
        requireUpdated(updatedRows, "rule_example_persona", personaId);
    }

    public void reorderPersonas(List<RuleCatalogOrderEntry> orderEntries) {
        requireOrderEntries(orderEntries, "orderEntries");
        for (RuleCatalogOrderEntry orderEntry : orderEntries) {
            requireUpdated(
                    jdbcTemplate.update(
                            """
                            UPDATE rule_example_persona
                            SET display_order = display_order + ?,
                                updated_at = NOW(),
                                version = version + 1
                            WHERE persona_id = ?
                            """,
                            REORDER_OFFSET,
                            orderEntry.itemId()),
                    "rule_example_persona",
                    orderEntry.itemId());
        }
        for (RuleCatalogOrderEntry orderEntry : orderEntries) {
            requireUpdated(
                    jdbcTemplate.update(
                            """
                            UPDATE rule_example_persona
                            SET display_order = ?,
                                updated_at = NOW(),
                                version = version + 1
                            WHERE persona_id = ?
                            """,
                            orderEntry.displayOrder(),
                            orderEntry.itemId()),
                    "rule_example_persona",
                    orderEntry.itemId());
        }
    }

    public UUID insertPrompt(UUID personaId, RulePromptWriteCommand command) {
        UUID promptId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO rule_example_prompt(
                            persona_id, prompt_en, prompt_vi, display_order, enabled, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, NOW())
                        RETURNING prompt_id
                        """,
                        UUID.class,
                        personaId,
                        command.exampleTextEn(),
                        command.exampleTextVi(),
                        command.displayOrder(),
                        command.enabled());
        return Objects.requireNonNull(promptId, "inserted promptId must not be null");
    }

    public void updatePrompt(UUID promptId, RulePromptWriteCommand command) {
        int updatedRows =
                jdbcTemplate.update(
                        """
                        UPDATE rule_example_prompt
                        SET prompt_en = ?,
                            prompt_vi = ?,
                            display_order = ?,
                            enabled = ?,
                            updated_at = NOW(),
                            version = version + 1
                        WHERE prompt_id = ?
                        """,
                        command.exampleTextEn(),
                        command.exampleTextVi(),
                        command.displayOrder(),
                        command.enabled(),
                        promptId);
        requireUpdated(updatedRows, "rule_example_prompt", promptId);
    }

    public void setPromptEnabled(UUID promptId, boolean enabled) {
        int updatedRows =
                jdbcTemplate.update(
                        """
                        UPDATE rule_example_prompt
                        SET enabled = ?,
                            updated_at = NOW(),
                            version = version + 1
                        WHERE prompt_id = ?
                        """,
                        enabled,
                        promptId);
        requireUpdated(updatedRows, "rule_example_prompt", promptId);
    }

    public void reorderPrompts(UUID personaId, List<RuleCatalogOrderEntry> orderEntries) {
        Objects.requireNonNull(personaId, "personaId must not be null");
        requireOrderEntries(orderEntries, "orderEntries");
        for (RuleCatalogOrderEntry orderEntry : orderEntries) {
            requireUpdated(
                    jdbcTemplate.update(
                            """
                            UPDATE rule_example_prompt
                            SET display_order = display_order + ?,
                                updated_at = NOW(),
                                version = version + 1
                            WHERE persona_id = ?
                              AND prompt_id = ?
                            """,
                            REORDER_OFFSET,
                            personaId,
                            orderEntry.itemId()),
                    "rule_example_prompt",
                    orderEntry.itemId());
        }
        for (RuleCatalogOrderEntry orderEntry : orderEntries) {
            requireUpdated(
                    jdbcTemplate.update(
                            """
                            UPDATE rule_example_prompt
                            SET display_order = ?,
                                updated_at = NOW(),
                                version = version + 1
                            WHERE persona_id = ?
                              AND prompt_id = ?
                            """,
                            orderEntry.displayOrder(),
                            personaId,
                            orderEntry.itemId()),
                    "rule_example_prompt",
                    orderEntry.itemId());
        }
    }

    public void upsertActionDescriptor(RuleActionDescriptorWriteCommand command) {
        jdbcTemplate.update(
                """
                INSERT INTO rule_action_descriptor(
                    action_key, label_en, label_vi, description_en, description_vi, risk_level,
                    availability_status, display_order, enabled, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (action_key) DO UPDATE
                SET label_en = EXCLUDED.label_en,
                    label_vi = EXCLUDED.label_vi,
                    description_en = EXCLUDED.description_en,
                    description_vi = EXCLUDED.description_vi,
                    risk_level = EXCLUDED.risk_level,
                    availability_status = EXCLUDED.availability_status,
                    display_order = EXCLUDED.display_order,
                    enabled = EXCLUDED.enabled,
                    updated_at = NOW(),
                    version = rule_action_descriptor.version + 1
                """,
                command.actionKey(),
                command.labelEn(),
                command.labelVi(),
                command.descriptionEn(),
                command.descriptionVi(),
                command.riskLevel(),
                command.availabilityStatus(),
                command.displayOrder(),
                command.enabled());
    }

    public void setActionDescriptorEnabled(String actionKey, boolean enabled) {
        int updatedRows =
                jdbcTemplate.update(
                        """
                        UPDATE rule_action_descriptor
                        SET enabled = ?,
                            updated_at = NOW(),
                            version = version + 1
                        WHERE action_key = ?
                        """,
                        enabled,
                        actionKey);
        requireUpdated(updatedRows, "rule_action_descriptor", actionKey);
    }

    public void reorderActionDescriptors(List<RuleActionDescriptorOrderEntry> orderEntries) {
        if (orderEntries == null || orderEntries.isEmpty()) {
            throw new IllegalArgumentException("orderEntries must not be empty");
        }
        for (RuleActionDescriptorOrderEntry orderEntry : orderEntries) {
            requireUpdated(
                    jdbcTemplate.update(
                            """
                            UPDATE rule_action_descriptor
                            SET display_order = display_order + ?,
                                updated_at = NOW(),
                                version = version + 1
                            WHERE action_key = ?
                            """,
                            REORDER_OFFSET,
                            orderEntry.actionKey()),
                    "rule_action_descriptor",
                    orderEntry.actionKey());
        }
        for (RuleActionDescriptorOrderEntry orderEntry : orderEntries) {
            requireUpdated(
                    jdbcTemplate.update(
                            """
                            UPDATE rule_action_descriptor
                            SET display_order = ?,
                                updated_at = NOW(),
                                version = version + 1
                            WHERE action_key = ?
                            """,
                            orderEntry.displayOrder(),
                            orderEntry.actionKey()),
                    "rule_action_descriptor",
                    orderEntry.actionKey());
        }
    }

    public Map<String, Object> findPersonaState(UUID personaId) {
        return findRequiredMap(
                """
                SELECT persona_id,
                       persona_key,
                       display_name_en,
                       display_name_vi,
                       icon,
                       display_order,
                       enabled
                FROM rule_example_persona
                WHERE persona_id = ?
                """,
                "rule_example_persona",
                personaId,
                personaId);
    }

    public Map<String, Object> findPromptState(UUID promptId) {
        return findRequiredMap(
                """
                SELECT prompt_id,
                       persona_id,
                       prompt_en,
                       prompt_vi,
                       display_order,
                       enabled
                FROM rule_example_prompt
                WHERE prompt_id = ?
                """,
                "rule_example_prompt",
                promptId,
                promptId);
    }

    public Map<String, Object> findActionDescriptorState(String actionKey) {
        return findRequiredMap(
                """
                SELECT action_key,
                       label_en,
                       label_vi,
                       description_en,
                       description_vi,
                       risk_level,
                       availability_status,
                       display_order,
                       enabled
                FROM rule_action_descriptor
                WHERE action_key = ?
                """,
                "rule_action_descriptor",
                actionKey,
                actionKey);
    }

    public Map<String, Object> findActionDescriptorStateOrEmpty(String actionKey) {
        List<Map<String, Object>> states =
                jdbcTemplate.queryForList(
                        """
                        SELECT action_key,
                               label_en,
                               label_vi,
                               description_en,
                               description_vi,
                               risk_level,
                               availability_status,
                               display_order,
                               enabled
                        FROM rule_action_descriptor
                        WHERE action_key = ?
                        """,
                        actionKey);
        return states.isEmpty() ? Map.of() : states.getFirst();
    }

    public List<Map<String, Object>> findPersonaOrderState() {
        return jdbcTemplate.queryForList(
                """
                SELECT persona_id, persona_key, display_order
                FROM rule_example_persona
                ORDER BY display_order
                """);
    }

    public List<Map<String, Object>> findPromptOrderState(UUID personaId) {
        return jdbcTemplate.queryForList(
                """
                SELECT prompt_id, display_order
                FROM rule_example_prompt
                WHERE persona_id = ?
                ORDER BY display_order
                """,
                personaId);
    }

    public List<Map<String, Object>> findActionDescriptorOrderState() {
        return jdbcTemplate.queryForList(
                """
                SELECT action_key, display_order
                FROM rule_action_descriptor
                ORDER BY display_order
                """);
    }

    private static void addPersonaViewRow(
            LinkedHashMap<UUID, PersonaViewAccumulator> personasById, ResultSet resultSet)
            throws SQLException {
        UUID personaId = resultSet.getObject("persona_id", UUID.class);
        String personaKey = resultSet.getString("persona_key");
        String displayName = resultSet.getString("display_name");
        String icon = resultSet.getString("icon");
        int personaDisplayOrder = resultSet.getInt("persona_display_order");
        PersonaViewAccumulator personaViewAccumulator =
                personasById.computeIfAbsent(
                        personaId,
                        _ ->
                                new PersonaViewAccumulator(
                                        personaId,
                                        personaKey,
                                        displayName,
                                        icon,
                                        personaDisplayOrder));
        personaViewAccumulator.prompts.add(
                new RuleExamplePromptView(
                        resultSet.getObject("prompt_id", UUID.class),
                        resultSet.getString("prompt_text"),
                        resultSet.getInt("prompt_display_order")));
    }

    private static void addPersonaAdminRow(
            LinkedHashMap<UUID, PersonaAdminAccumulator> personasById, ResultSet resultSet)
            throws SQLException {
        UUID personaId = resultSet.getObject("persona_id", UUID.class);
        String personaKey = resultSet.getString("persona_key");
        String displayNameEn = resultSet.getString("display_name_en");
        String displayNameVi = resultSet.getString("display_name_vi");
        String icon = resultSet.getString("icon");
        int personaDisplayOrder = resultSet.getInt("persona_display_order");
        boolean personaEnabled = resultSet.getBoolean("persona_enabled");
        PersonaAdminAccumulator personaAdminAccumulator =
                personasById.computeIfAbsent(
                        personaId,
                        _ ->
                                new PersonaAdminAccumulator(
                                        personaId,
                                        personaKey,
                                        displayNameEn,
                                        displayNameVi,
                                        icon,
                                        personaDisplayOrder,
                                        personaEnabled));
        UUID promptId = resultSet.getObject("prompt_id", UUID.class);
        if (promptId != null) {
            personaAdminAccumulator.prompts.add(
                    new RuleExamplePromptAdminView(
                            promptId,
                            resultSet.getString("prompt_en"),
                            resultSet.getString("prompt_vi"),
                            resultSet.getInt("prompt_display_order"),
                            resultSet.getBoolean("prompt_enabled")));
        }
    }

    private static RuleActionDescriptorView mapActionDescriptorView(
            ResultSet resultSet, int rowNumber) throws SQLException {
        return new RuleActionDescriptorView(
                resultSet.getString("action_key"),
                resultSet.getString("label"),
                resultSet.getString("description"),
                resultSet.getString("risk_level"),
                resultSet.getString("availability_status"),
                resultSet.getInt("display_order"));
    }

    private static RuleActionDescriptorAdminView mapActionDescriptorAdminView(
            ResultSet resultSet, int rowNumber) throws SQLException {
        return new RuleActionDescriptorAdminView(
                resultSet.getString("action_key"),
                resultSet.getString("label_en"),
                resultSet.getString("label_vi"),
                resultSet.getString("description_en"),
                resultSet.getString("description_vi"),
                resultSet.getString("risk_level"),
                resultSet.getString("availability_status"),
                resultSet.getInt("display_order"),
                resultSet.getBoolean("enabled"));
    }

    private Map<String, Object> findRequiredMap(
            String sql, String tableName, Object identifier, Object... arguments) {
        List<Map<String, Object>> states = jdbcTemplate.queryForList(sql, arguments);
        if (states.isEmpty()) {
            throw new NoSuchElementException(tableName + " not found: " + identifier);
        }
        return states.getFirst();
    }

    private static void requireOrderEntries(
            List<RuleCatalogOrderEntry> orderEntries, String parameterName) {
        if (orderEntries == null || orderEntries.isEmpty()) {
            throw new IllegalArgumentException(parameterName + " must not be empty");
        }
    }

    private static void requireUpdated(int updatedRows, String tableName, Object identifier) {
        if (updatedRows == 0) {
            throw new NoSuchElementException(tableName + " not found: " + identifier);
        }
    }

    private static final class PersonaViewAccumulator {

        private final UUID personaId;
        private final String personaKey;
        private final String displayName;
        private final String icon;
        private final int displayOrder;
        private final List<RuleExamplePromptView> prompts = new ArrayList<>();

        private PersonaViewAccumulator(
                UUID personaId,
                String personaKey,
                String displayName,
                String icon,
                int displayOrder) {
            this.personaId = personaId;
            this.personaKey = personaKey;
            this.displayName = displayName;
            this.icon = icon;
            this.displayOrder = displayOrder;
        }

        private RuleExamplePersonaView toView() {
            return new RuleExamplePersonaView(
                    personaId, personaKey, displayName, icon, displayOrder, List.copyOf(prompts));
        }
    }

    private static final class PersonaAdminAccumulator {

        private final UUID personaId;
        private final String personaKey;
        private final String displayNameEn;
        private final String displayNameVi;
        private final String icon;
        private final int displayOrder;
        private final boolean enabled;
        private final List<RuleExamplePromptAdminView> prompts = new ArrayList<>();

        private PersonaAdminAccumulator(
                UUID personaId,
                String personaKey,
                String displayNameEn,
                String displayNameVi,
                String icon,
                int displayOrder,
                boolean enabled) {
            this.personaId = personaId;
            this.personaKey = personaKey;
            this.displayNameEn = displayNameEn;
            this.displayNameVi = displayNameVi;
            this.icon = icon;
            this.displayOrder = displayOrder;
            this.enabled = enabled;
        }

        private RuleExamplePersonaAdminView toView() {
            return new RuleExamplePersonaAdminView(
                    personaId,
                    personaKey,
                    displayNameEn,
                    displayNameVi,
                    icon,
                    displayOrder,
                    enabled,
                    List.copyOf(prompts));
        }
    }
}
