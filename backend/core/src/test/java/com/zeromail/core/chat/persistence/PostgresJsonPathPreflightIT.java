package com.zeromail.core.chat.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Preflight for the exact Postgres 18 body-key detector used by reject_chat_message_with_body().
 *
 * <p>The first reviewed JSONPath candidate used {@code $.**.keyvalue()}, but Postgres 18 rejects
 * that against recursive scalar nodes with "jsonpath item method .keyvalue() can only be applied to
 * an object". The trigger therefore uses the recursive PL/pgSQL function validated here: {@code
 * chat_jsonb_contains_body_key(jsonb)} built on {@code jsonb_each} and {@code
 * jsonb_array_elements}.
 *
 * <p>The trigger combines this detector with exact part-type checks:
 * tool-getMessage/tool-searchInbox/tool-getThread/tool-listLabels are banned sources, while
 * tool-sendEmail/tool-replyEmail/tool-forwardEmail/tool-saveDraft are user-authored draft sources.
 */
class PostgresJsonPathPreflightIT {

    private static final List<String> EMAIL_READ_TOOL_TYPES =
            List.of("tool-getMessage", "tool-searchInbox", "tool-getThread", "tool-listLabels");
    private static final List<String> DRAFT_TOOL_TYPES =
            List.of("tool-sendEmail", "tool-replyEmail", "tool-forwardEmail", "tool-saveDraft");

    @Test
    void recursive_body_key_function_detects_body_fields_for_each_trigger_source_type()
            throws Exception {
        try (PostgreSQLContainer<?> postgresContainer =
                new PostgreSQLContainer<>("postgres:18.4").withDatabaseName("jsonpath_preflight")) {
            postgresContainer.start();
            try (Connection connection =
                    DriverManager.getConnection(
                            postgresContainer.getJdbcUrl(),
                            postgresContainer.getUsername(),
                            postgresContainer.getPassword())) {
                createTemporaryFixtureTable(connection);
                createBodyKeyDetectorFunction(connection);

                for (String emailReadToolType : EMAIL_READ_TOOL_TYPES) {
                    String partJson = partWithNestedBodyField(emailReadToolType, "htmlBody");

                    assertThat(hasBodyKey(connection, partJson))
                            .as("%s must be detected as body-bearing", emailReadToolType)
                            .isTrue();
                    assertThat(isEmailReadToolType(emailReadToolType)).isTrue();
                }

                for (String draftToolType : DRAFT_TOOL_TYPES) {
                    String partJson = partWithNestedBodyField(draftToolType, "body");

                    assertThat(hasBodyKey(connection, partJson))
                            .as(
                                    "%s still has a body key, but trigger source policy allows it",
                                    draftToolType)
                            .isTrue();
                    assertThat(isDraftToolType(draftToolType)).isTrue();
                }

                assertThat(hasBodyKey(connection, metadataOnlyPart("tool-getMessage"))).isFalse();
            }
        }
    }

    private static void createTemporaryFixtureTable(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("CREATE TEMP TABLE jsonpath_preflight(parts jsonb)")) {
            statement.execute();
        }
    }

    private static void createBodyKeyDetectorFunction(Connection connection) throws SQLException {
        String functionSql =
                """
                CREATE OR REPLACE FUNCTION chat_jsonb_contains_body_key(candidate jsonb)
                RETURNS boolean
                LANGUAGE plpgsql
                AS $$
                DECLARE
                  object_entry record;
                  array_element jsonb;
                BEGIN
                  IF candidate IS NULL THEN
                    RETURN FALSE;
                  END IF;

                  IF jsonb_typeof(candidate) = 'object' THEN
                    FOR object_entry IN SELECT key, value FROM jsonb_each(candidate) LOOP
                      IF object_entry.key ~* '^(body|emailBody|messageBody|bodyHtml|bodyText|htmlBody|textBody)$' THEN
                        RETURN TRUE;
                      END IF;
                      IF chat_jsonb_contains_body_key(object_entry.value) THEN
                        RETURN TRUE;
                      END IF;
                    END LOOP;
                  ELSIF jsonb_typeof(candidate) = 'array' THEN
                    FOR array_element IN SELECT value FROM jsonb_array_elements(candidate) LOOP
                      IF chat_jsonb_contains_body_key(array_element) THEN
                        RETURN TRUE;
                      END IF;
                    END LOOP;
                  END IF;

                  RETURN FALSE;
                END;
                $$;
                """;
        try (PreparedStatement statement = connection.prepareStatement(functionSql)) {
            statement.execute();
        }
    }

    private static boolean hasBodyKey(Connection connection, String partJson) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT chat_jsonb_contains_body_key(?::jsonb)")) {
            statement.setString(1, partJson);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static String partWithNestedBodyField(String toolType, String bodyFieldName) {
        return """
                {
                  "type": "%s",
                  "output": {
                    "messageId": "message-1",
                    "nested": {
                      "%s": "secret body"
                    }
                  }
                }
                """
                .formatted(toolType, bodyFieldName);
    }

    private static String metadataOnlyPart(String toolType) {
        return """
                {
                  "type": "%s",
                  "output": {
                    "messageId": "message-1",
                    "snippet": "short metadata only"
                  }
                }
                """
                .formatted(toolType);
    }

    private static boolean isEmailReadToolType(String toolType) {
        return EMAIL_READ_TOOL_TYPES.contains(toolType);
    }

    private static boolean isDraftToolType(String toolType) {
        return DRAFT_TOOL_TYPES.contains(toolType);
    }
}
