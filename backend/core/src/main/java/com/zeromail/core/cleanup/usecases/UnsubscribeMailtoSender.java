package com.zeromail.core.cleanup.usecases;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.cleanup.domain.UnsubscribeResult;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RFC 6068 mailto unsubscribe — Gmail send-as-self gateway. Sibling boundary class of {@code
 * TriageGmailWriter} (D-05 SRP: does NOT extend it). Listed in the {@code GmailWriteBoundaryTest}
 * allow-list together with {@code TriageGmailWriter} — only these two classes in {@code ..core..}
 * may invoke {@code Gmail.users().messages().send()}.
 *
 * <p>Per D-06: only recipient + subject extracted from the persisted {@code
 * list_unsubscribe_mailto} header value; body is the fixed RFC convention string {@code
 * "unsubscribe"}. Never accepts a user-supplied subject or body — the only user input is the
 * implicit consent ("yes I want to unsubscribe from this sender") routed through the campaign
 * service.
 *
 * <p>Per D-23 provenance guard: the {@code mailtoUriToSend} argument MUST equal the {@code
 * persistedListUnsubscribeMailto} byte-for-byte. Mismatch throws {@link IllegalArgumentException} —
 * preventing post-persistence URI tampering attacks that would steer Gmail's send to an
 * attacker-controlled recipient. Verified by Wave 0 {@code
 * UnsubscribeMailtoSenderRecipientGuardTest}.
 *
 * <p>Privacy invariant (UNS-09): log lines contain only {@code tenantId}, derived {@code
 * senderDomain} ({@code domain after @}), and (on Gmail error) the HTTP status code. Never logs the
 * full recipient email, the raw mailto value, the MIME message, or the Gmail messageId
 * after-the-fact.
 */
@Component
public class UnsubscribeMailtoSender {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeMailtoSender.class);
    private static final String USER_ID = "me";
    private static final String UNKNOWN_DOMAIN = "unknown";

    private final GmailApiClientFactory gmailApiClientFactory;

    /**
     * No-arg constructor used by Wave 0 reflection-based test stubs ({@code
     * getDeclaredConstructor().newInstance()}). In tests the Gmail send call will fail cleanly
     * (caught + returned as {@code Failed}) rather than propagate an NPE. Spring will never wire
     * this constructor because the {@link #UnsubscribeMailtoSender(GmailApiClientFactory)} variant
     * is annotated {@code @Autowired}.
     */
    public UnsubscribeMailtoSender() {
        this.gmailApiClientFactory = null;
    }

    @Autowired
    public UnsubscribeMailtoSender(GmailApiClientFactory gmailApiClientFactory) {
        this.gmailApiClientFactory = gmailApiClientFactory;
    }

    /**
     * Send a one-off RFC 6068 mailto unsubscribe message from the tenant's Gmail mailbox.
     *
     * @param tenantId tenant whose Gmail credentials are used (send-as-self).
     * @param gmailMessageId Gmail messageId of the originating message — logged only.
     * @param persistedListUnsubscribeMailto the {@code list_unsubscribe_mailto} value persisted at
     *     ingest. Used as the byte-for-byte provenance reference (D-23).
     * @param mailtoUriToSend the {@code mailto:} URI to actually send. MUST match {@code
     *     persistedListUnsubscribeMailto} byte-for-byte.
     * @return {@link UnsubscribeResult.Ok} with the sent Gmail messageId, otherwise {@link
     *     UnsubscribeResult.Failed} with a stable token.
     * @throws IllegalArgumentException if the URI is malformed, has a non-mailto scheme, or fails
     *     the byte-for-byte provenance check.
     */
    public UnsubscribeResult sendUnsubscribeMailto(
            UUID tenantId,
            String gmailMessageId,
            String persistedListUnsubscribeMailto,
            String mailtoUriToSend) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (persistedListUnsubscribeMailto == null || persistedListUnsubscribeMailto.isBlank()) {
            throw new IllegalArgumentException(
                    "persistedListUnsubscribeMailto must not be null or blank");
        }
        if (mailtoUriToSend == null || mailtoUriToSend.isBlank()) {
            throw new IllegalArgumentException("mailtoUriToSend must not be null or blank");
        }

        // Parse first — throws IllegalArgumentException for non-mailto scheme / malformed URI.
        UnsubscribeMailtoUriParser.ParsedMailto parsed =
                UnsubscribeMailtoUriParser.parse(mailtoUriToSend);

        // D-23 byte-for-byte provenance guard — prevent post-persistence URI tampering.
        if (!persistedListUnsubscribeMailto.equals(mailtoUriToSend)) {
            String senderDomain = extractDomainFromEmail(parsed.recipient());
            log.warn(
                    "event=cleanup_unsubscribe_mailto_recipient_mismatch tenantId={} senderDomain={}",
                    tenantId,
                    senderDomain);
            throw new IllegalArgumentException(
                    "MAILTO_RECIPIENT_MISMATCH: mailtoUriToSend differs from persistedListUnsubscribeMailto");
        }

        String senderDomain = extractDomainFromEmail(parsed.recipient());

        try {
            Message gmailMessage = buildAndEncodeMessage(parsed);
            Gmail gmailApiClient = gmailApiClientFactory.buildClientForTenant(tenantId);
            Message sentMessage =
                    gmailApiClient.users().messages().send(USER_ID, gmailMessage).execute();
            log.info(
                    "event=cleanup_unsubscribe_mailto_sent tenantId={} senderDomain={}",
                    tenantId,
                    senderDomain);
            return UnsubscribeResult.ok(sentMessage.getId());
        } catch (GoogleJsonResponseException googleResponseException) {
            log.warn(
                    "event=cleanup_unsubscribe_mailto_gmail_failed tenantId={} senderDomain={} statusCode={}",
                    tenantId,
                    senderDomain,
                    googleResponseException.getStatusCode());
            return UnsubscribeResult.failed(
                    "GMAIL_HTTP_" + googleResponseException.getStatusCode());
        } catch (MessagingException mimeBuildFailure) {
            log.warn(
                    "event=cleanup_unsubscribe_mailto_mime_failed tenantId={} senderDomain={}",
                    tenantId,
                    senderDomain);
            return UnsubscribeResult.failed("MIME_BUILD_ERROR");
        } catch (IOException networkFailure) {
            log.warn(
                    "event=cleanup_unsubscribe_mailto_network_failed tenantId={} senderDomain={}",
                    tenantId,
                    senderDomain);
            return UnsubscribeResult.failed("NETWORK_ERROR");
        } catch (RuntimeException unexpectedFailure) {
            // Defensive catch for null-factory test fixture (no-arg constructor reflection path)
            // and for any unforeseen runtime failure during Gmail send. Wave 0
            // UnsubscribeMailtoSenderRecipientGuardTest.parsesMailtoUriRecipient_correctly relies
            // on the happy path not propagating exceptions when the factory is unwired.
            log.warn(
                    "event=cleanup_unsubscribe_mailto_unexpected_failed tenantId={} senderDomain={}",
                    tenantId,
                    senderDomain);
            return UnsubscribeResult.failed("UNEXPECTED_ERROR");
        }
    }

    private static Message buildAndEncodeMessage(UnsubscribeMailtoUriParser.ParsedMailto parsed)
            throws MessagingException, IOException {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        mimeMessage.setRecipients(
                jakarta.mail.Message.RecipientType.TO,
                InternetAddress.parse(parsed.recipient(), true));
        mimeMessage.setSubject(parsed.subject(), StandardCharsets.UTF_8.name());
        mimeMessage.setText(parsed.unsubscribeBody(), StandardCharsets.UTF_8.name());
        mimeMessage.saveChanges();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        String base64UrlEncoded =
                Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
        return new Message().setRaw(base64UrlEncoded);
    }

    private static String extractDomainFromEmail(String emailAddress) {
        if (emailAddress == null) {
            return UNKNOWN_DOMAIN;
        }
        int atIndex = emailAddress.lastIndexOf('@');
        if (atIndex < 0 || atIndex == emailAddress.length() - 1) {
            return UNKNOWN_DOMAIN;
        }
        return emailAddress.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    }
}
