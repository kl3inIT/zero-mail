package com.zeromail.core.cleanup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * D-06 + D-23 — Recipient provenance guard for the mailto: unsubscribe path.
 *
 * <p>Future {@code UnsubscribeMailtoSender.sendUnsubscribeMailto(...)}:
 *
 * <ul>
 *   <li>parses the {@code mailto:} URI via {@link java.net.URI} (RFC 6068 conformant in Java 25),
 *       extracting the recipient and any subject/body query parameters.
 *   <li>rejects any URI whose scheme is not {@code mailto:}.
 *   <li>rejects any URI whose parsed recipient does not match the persisted {@code
 *       list_unsubscribe_mailto} value for that message — preventing post-persistence URI tampering
 *       from steering Gmail send-as-self to an arbitrary recipient.
 * </ul>
 *
 * <p>Wave 0 RED: {@code UnsubscribeMailtoSender} not yet present; reflective lookup fails.
 */
class UnsubscribeMailtoSenderRecipientGuardTest {

    private static final String UNSUBSCRIBE_MAILTO_SENDER =
            "com.zeromail.core.cleanup.usecases.UnsubscribeMailtoSender";

    @Test
    void future_unsubscribe_mailto_sender_type_is_present() {
        assertThatCode(() -> Class.forName(UNSUBSCRIBE_MAILTO_SENDER))
                .as("Future production type must exist: " + UNSUBSCRIBE_MAILTO_SENDER)
                .doesNotThrowAnyException();
    }

    @Test
    void parsesMailtoUriRecipient_correctly() throws Exception {
        Class.forName(UNSUBSCRIBE_MAILTO_SENDER);
        Object unsubscribeMailtoSender = lookupBean();
        UUID tenantId = UUID.randomUUID();
        String persistedMailto = "mailto:unsub@provider.test";

        // Wave 3 will return a non-null MailtoSendResult on success; for now we only assert
        // that the happy-path call surface does not throw IllegalArgumentException once the
        // recipient is taken verbatim from the persisted header.
        assertThatCode(
                        () ->
                                invokeSendUnsubscribeMailto(
                                        unsubscribeMailtoSender,
                                        tenantId,
                                        persistedMailto,
                                        persistedMailto))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUriIfSchemeNotMailto() throws Exception {
        Class.forName(UNSUBSCRIBE_MAILTO_SENDER);
        Object unsubscribeMailtoSender = lookupBean();
        UUID tenantId = UUID.randomUUID();
        String persistedMailto = "mailto:unsub@provider.test";
        String tamperedHttpsUri = "https://provider.test/u/x";

        assertThatThrownBy(
                        () ->
                                invokeSendUnsubscribeMailto(
                                        unsubscribeMailtoSender,
                                        tenantId,
                                        persistedMailto,
                                        tamperedHttpsUri))
                .as("non-mailto scheme must be rejected")
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIfParsedRecipientDoesNotMatchPersistedHeader() throws Exception {
        Class.forName(UNSUBSCRIBE_MAILTO_SENDER);
        Object unsubscribeMailtoSender = lookupBean();
        UUID tenantId = UUID.randomUUID();
        String persistedMailto = "mailto:unsub@provider.test";
        String tamperedMailto = "mailto:attacker@malicious.test";

        assertThatThrownBy(
                        () ->
                                invokeSendUnsubscribeMailto(
                                        unsubscribeMailtoSender,
                                        tenantId,
                                        persistedMailto,
                                        tamperedMailto))
                .as("recipient mismatch vs persisted header must be rejected")
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private static Object lookupBean() throws Exception {
        return Class.forName(UNSUBSCRIBE_MAILTO_SENDER).getDeclaredConstructor().newInstance();
    }

    private static Object invokeSendUnsubscribeMailto(
            Object unsubscribeMailtoSender,
            UUID tenantId,
            String persistedListUnsubscribeMailto,
            String mailtoUriToSend) {
        try {
            return unsubscribeMailtoSender
                    .getClass()
                    .getMethod("sendUnsubscribeMailto", UUID.class, String.class, String.class)
                    .invoke(
                            unsubscribeMailtoSender,
                            tenantId,
                            persistedListUnsubscribeMailto,
                            mailtoUriToSend);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
    }
}
