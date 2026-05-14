package com.zeromail.worker.notification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.domain.DigestRuleHit;
import com.zeromail.core.notification.domain.DigestTopSender;
import com.zeromail.core.notification.domain.DigestTotals;
import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.worker.notification.config.NotificationProperties;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailNotificationChannelTest {

    private static final String RECIPIENT_ADDRESS = "digest-recipient@example.test";

    @Test
    void resend_gateway_sets_idempotency_header_digest_tag_and_returns_email_id() throws Exception {
        Resend resendClient = mock(Resend.class);
        Emails emails = mock(Emails.class);
        when(resendClient.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class)))
                .thenReturn(new CreateEmailResponse("res_email_05c"));
        ResendEmailGateway gateway = new ResendEmailGateway(properties(), resendClient);

        DispatchOutcome outcome =
                gateway.send(
                        "notifications@zero-mail.test",
                        RECIPIENT_ADDRESS,
                        "subject",
                        "<p>html</p>",
                        "text",
                        "tenant-id:2026-05-13");

        assertThat(outcome).isEqualTo(new DispatchOutcome.Success("res_email_05c"));
        ArgumentCaptor<CreateEmailOptions> emailOptionsCaptor =
                ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(emailOptionsCaptor.capture());
        CreateEmailOptions emailOptions = emailOptionsCaptor.getValue();
        assertThat(emailOptions.getHeaders())
                .containsEntry("Idempotency-Key", "tenant-id:2026-05-13");
        assertThat(emailOptions.getTags())
                .singleElement()
                .satisfies(
                        tag -> {
                            assertThat(tag.getName()).isEqualTo("category");
                            assertThat(tag.getValue()).isEqualTo("digest");
                        });
        assertThat(emailOptions.getHtml()).isEqualTo("<p>html</p>");
        assertThat(emailOptions.getText()).isEqualTo("text");
    }

    @Test
    void resend_gateway_classifies_permanent_and_transient_failures() throws Exception {
        Resend permanentResendClient = mock(Resend.class);
        Emails permanentEmails = mock(Emails.class);
        when(permanentResendClient.emails()).thenReturn(permanentEmails);
        when(permanentEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException(422, "unprocessable"));
        ResendEmailGateway permanentGateway =
                new ResendEmailGateway(properties(), permanentResendClient);

        assertThat(sendFixture(permanentGateway))
                .isEqualTo(new DispatchOutcome.PermanentFailure("resend_4xx_422"));

        Resend transientResendClient = mock(Resend.class);
        Emails transientEmails = mock(Emails.class);
        when(transientResendClient.emails()).thenReturn(transientEmails);
        when(transientEmails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException(429, "rate limited"));
        ResendEmailGateway transientGateway =
                new ResendEmailGateway(properties(), transientResendClient);

        assertThat(sendFixture(transientGateway))
                .isEqualTo(new DispatchOutcome.TransientFailure("resend_transient_429"));
    }

    @Test
    void email_channel_returns_permanent_failure_for_missing_recipient_without_resend_call() {
        ThymeleafDigestRenderer renderer = mock(ThymeleafDigestRenderer.class);
        ResendEmailGateway gateway = mock(ResendEmailGateway.class);
        EmailNotificationChannel channel =
                new EmailNotificationChannel(renderer, gateway, properties());

        DispatchOutcome nullOutcome = channel.dispatch(activePayload(Locale.ENGLISH), null);
        DispatchOutcome blankOutcome = channel.dispatch(activePayload(Locale.ENGLISH), " ");

        assertThat(nullOutcome).isEqualTo(new DispatchOutcome.PermanentFailure("no_email_found"));
        assertThat(blankOutcome).isEqualTo(new DispatchOutcome.PermanentFailure("no_email_found"));
        verify(gateway, never())
                .send(
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class));
    }

    @Test
    void email_channel_renders_bodies_and_uses_tenant_day_idempotency_key() {
        DigestPayload payload = activePayload(Locale.ENGLISH);
        ThymeleafDigestRenderer renderer = mock(ThymeleafDigestRenderer.class);
        when(renderer.subject(payload)).thenReturn("subject");
        when(renderer.renderHtml(payload)).thenReturn("<p>html</p>");
        when(renderer.renderText(payload)).thenReturn("text");
        ResendEmailGateway gateway = mock(ResendEmailGateway.class);
        when(gateway.send(
                        "notifications@zero-mail.test",
                        RECIPIENT_ADDRESS,
                        "subject",
                        "<p>html</p>",
                        "text",
                        payload.tenantId() + ":" + payload.digestDayLocal()))
                .thenReturn(new DispatchOutcome.Success("res_email_05c"));
        EmailNotificationChannel channel =
                new EmailNotificationChannel(renderer, gateway, properties());

        DispatchOutcome outcome = channel.dispatch(payload, RECIPIENT_ADDRESS);

        assertThat(outcome).isEqualTo(new DispatchOutcome.Success("res_email_05c"));
        verify(gateway)
                .send(
                        "notifications@zero-mail.test",
                        RECIPIENT_ADDRESS,
                        "subject",
                        "<p>html</p>",
                        "text",
                        payload.tenantId() + ":" + payload.digestDayLocal());
    }

    private static DispatchOutcome sendFixture(ResendEmailGateway gateway) {
        return gateway.send(
                "notifications@zero-mail.test",
                RECIPIENT_ADDRESS,
                "subject",
                "<p>html</p>",
                "text",
                "tenant-id:2026-05-13");
    }

    private static NotificationProperties properties() {
        return new NotificationProperties(
                new NotificationProperties.EmailProperties(
                        new NotificationProperties.ResendProperties("test-resend-key"),
                        "notifications@zero-mail.test"),
                URI.create("https://zero-mail.test"));
    }

    private static DigestPayload activePayload(Locale locale) {
        return new DigestPayload(
                locale,
                UUID.fromString("00000000-0000-0000-0000-000000005c03"),
                LocalDate.parse("2026-05-13"),
                new DigestTotals(60, 47, 18 * 60),
                List.of(
                        new DigestTopSender("founder@example.test", 47),
                        new DigestTopSender("alerts@example.test", 8)),
                List.of(new DigestRuleHit("Archive invoices", 12, 1)),
                URI.create("https://zero-mail.test/analytics?source=digest&window=7d"),
                URI.create("https://zero-mail.test/settings?section=notifications&source=digest"),
                false);
    }
}
