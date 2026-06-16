package com.zeromail.core.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.inbox.domain.EncryptedField;
import com.zeromail.core.inbox.usecases.InboxProjectionCipher;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RED contract for ING-06 projection migration.
 *
 * <p>Waits on schema primary key {@code (tenant_id, gmail_connection_id, gmail_message_id)} for
 * {@code gmail_inbox_projection}, but directly references existing {@link InboxProjectionCipher} to
 * prove the AES-GCM AAD remains {@code tenantId:gmailMessageId:field}. The compile-green probe is
 * information_schema for the future PK column; the cipher assertion is expected to stay green
 * throughout the migration.
 */
class ProjectionAadContinuityTest extends PostgresContainerTest {

    @Autowired InboxProjectionCipher inboxProjectionCipher;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void projectionCipherStillDecryptsWithTenantMessageFieldAadOnly() {
        UUID tenantId = UUID.randomUUID();
        String gmailMessageId = "projection-aad-continuity";
        byte[] encryptedSubject =
                inboxProjectionCipher.encrypt(
                        "Quarterly plan", tenantId, gmailMessageId, EncryptedField.SUBJECT);

        String decryptedSubject =
                inboxProjectionCipher.decrypt(
                        encryptedSubject, tenantId, gmailMessageId, EncryptedField.SUBJECT);

        assertThat(decryptedSubject).isEqualTo("Quarterly plan");
    }

    @Test
    void projectionPrimaryKeyAddsMailboxWithoutChangingCipherAad() {
        assertThat(primaryKeyColumns("gmail_inbox_projection"))
                .as("projection PK must disambiguate same Gmail message ids across mailboxes")
                .containsExactly("tenant_id", "gmail_connection_id", "gmail_message_id");
    }

    private List<String> primaryKeyColumns(String tableName) {
        return jdbcTemplate.queryForList(
                """
                SELECT key_column_usage.column_name
                FROM information_schema.table_constraints table_constraints
                JOIN information_schema.key_column_usage key_column_usage
                  ON key_column_usage.constraint_name = table_constraints.constraint_name
                 AND key_column_usage.table_schema = table_constraints.table_schema
                 AND key_column_usage.table_name = table_constraints.table_name
                WHERE table_constraints.table_schema = 'public'
                  AND table_constraints.table_name = ?
                  AND table_constraints.constraint_type = 'PRIMARY KEY'
                ORDER BY key_column_usage.ordinal_position
                """,
                String.class,
                tableName);
    }
}
