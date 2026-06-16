package com.zeromail.core.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.migration.OldTwoMailboxFixture;
import com.zeromail.core.support.PostgresContainerTest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RED contract for ING-01/HIGH-2.
 *
 * <p>Waits on production symbol {@code PubSubTenantLookupRepository.findConnectedMailboxByEmail}
 * and record {@code TenantMailboxRef}. The test reaches both through FQN strings/reflection so it
 * compiles before the symbols exist. The schema assertion waits on global partial unique index
 * {@code uq_gmail_conn_active_email_global}; the duplicate-email assertion is a real JDBC round
 * trip and is RED until that index exists.
 */
class PubSubMailboxLookupTest extends PostgresContainerTest {

    private static final String LOOKUP_REPOSITORY_FQN =
            "com.zeromail.core.gmail.persistence.lowlevel.PubSubTenantLookupRepository";
    private static final String TENANT_MAILBOX_REF_FQN =
            "com.zeromail.core.gmail.persistence.lowlevel.TenantMailboxRef";

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void pubSubLookupExposesTenantAndMailboxReference() throws Exception {
        Class<?> lookupRepositoryClass = Class.forName(LOOKUP_REPOSITORY_FQN);
        Class<?> tenantMailboxRefClass =
                assertFutureClassPresent(TENANT_MAILBOX_REF_FQN, "tenant mailbox lookup result");

        Method lookupMethod =
                assertFutureMethodPresent(
                        lookupRepositoryClass, "findConnectedMailboxByEmail", String.class);

        assertThat(lookupMethod.getReturnType()).isEqualTo(Optional.class);
        assertThat(lookupMethod.getGenericReturnType().getTypeName())
                .contains(tenantMailboxRefClass.getName());
    }

    @Test
    void connectedGmailEmailIsGloballyUniqueAcrossTenants() {
        assertThat(indexDefinitions("uq_gmail_conn_active_email_global"))
                .as("changeset 127 must add a global CONNECTED-email partial unique index")
                .singleElement()
                .satisfies(
                        indexDefinition ->
                                assertThat(indexDefinition)
                                        .contains("lower")
                                        .contains("google_email")
                                        .contains("CONNECTED"));
    }

    @Test
    void duplicateConnectedEmailAcrossTenantsIsRejectedByDatabase() {
        OldTwoMailboxFixture mailboxFixture = new OldTwoMailboxFixture(jdbcTemplate);
        OldTwoMailboxFixture.SeededMailboxes seededMailboxes =
                mailboxFixture.seedConnectedMailboxes("pubsub-lookup");
        UUID otherTenantId = mailboxFixture.seedTenant("pubsub-lookup-other");

        assertThatThrownBy(
                        () ->
                                mailboxFixture.insertConnectedMailbox(
                                        otherTenantId, seededMailboxes.primaryEmail(), true))
                .as("same CONNECTED Gmail address must resolve to exactly one mailbox globally")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> indexDefinitions(String indexName) {
        return jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                String.class,
                indexName);
    }

    private static Class<?> assertFutureClassPresent(String className, String description) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException missingClass) {
            throw new AssertionError(
                    "Expected future " + description + " class to exist: " + className,
                    missingClass);
        }
    }

    private static Method assertFutureMethodPresent(
            Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        try {
            return targetClass.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException missingMethod) {
            throw new AssertionError(
                    "Expected future method "
                            + targetClass.getName()
                            + "."
                            + methodName
                            + " to exist",
                    missingMethod);
        }
    }
}
