package com.zeromail.core.rules.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.projection.GmailConnectionProjection;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService;
import com.zeromail.core.rules.domain.MatcherNode;
import com.zeromail.core.rules.domain.PreviewSampleSize;
import com.zeromail.core.rules.exception.GmailPreviewUnavailableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RulePreviewDataServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final Pattern FORBIDDEN_REPOSITORY_CONTENT_NAME =
            Pattern.compile("(?i)(rawHeaders|rawBody|snippet|completion)");

    @Test
    void returns_request_scoped_preview_inputs_without_raw_message_content() {
        GmailConnectionService gmailConnectionService = mock(GmailConnectionService.class);
        GmailPreviewReadService gmailPreviewReadService = mock(GmailPreviewReadService.class);
        when(gmailConnectionService.currentStatus(TENANT_ID))
                .thenReturn(
                        new GmailConnectionProjection(
                                "CONNECTED", "HEALTHY", "hidden@example.test"));
        when(gmailPreviewReadService.fetchRecentInboxMessages(
                        eq(TENANT_ID), eq(100), eq(false), eq(Duration.ofSeconds(5))))
                .thenReturn(List.of(gmailPreviewMessage()));

        RulePreviewDataService dataService =
                new RulePreviewDataService(gmailConnectionService, gmailPreviewReadService);

        List<RulePreviewDataService.PreviewInput> previewInputs =
                dataService.fetchPreviewInputs(
                        TENANT_ID,
                        new MatcherNode.SenderDomainMatcher("sender-domain", "stripe.com"),
                        PreviewSampleSize.normalize(null));

        assertThat(previewInputs).hasSize(1);
        RulePreviewDataService.PreviewInput previewInput = previewInputs.getFirst();
        assertThat(previewInput.gmailMessageId()).isEqualTo("gmail-1");
        assertThat(previewInput.summary().sanitizedSubjectExcerpt())
                .isEqualTo("Receipt from Stripe");
        assertThat(previewInput.ruleEvaluationInput().sanitizedBodyEvidencePresent()).isEmpty();
        assertThat(previewInput.toString())
                .doesNotContain("rawHeaders", "rawBody", "snippet", "prompt", "completion");
    }

    @Test
    void disconnected_and_revoked_grants_map_to_safe_preview_unavailable_reasons() {
        GmailConnectionService disconnectedConnectionService = mock(GmailConnectionService.class);
        GmailPreviewReadService gmailPreviewReadService = mock(GmailPreviewReadService.class);
        when(disconnectedConnectionService.currentStatus(TENANT_ID))
                .thenReturn(new GmailConnectionProjection("DISCONNECTED", "HEALTHY", null));

        RulePreviewDataService disconnectedDataService =
                new RulePreviewDataService(disconnectedConnectionService, gmailPreviewReadService);

        assertThatThrownBy(
                        () ->
                                disconnectedDataService.fetchPreviewInputs(
                                        TENANT_ID,
                                        new MatcherNode.SenderDomainMatcher(
                                                "sender-domain", "stripe.com"),
                                        new PreviewSampleSize(10)))
                .isInstanceOf(GmailPreviewUnavailableException.class)
                .extracting("reason")
                .isEqualTo(GmailPreviewUnavailableException.Reason.DISCONNECTED);
        verify(gmailPreviewReadService, never())
                .fetchRecentInboxMessages(
                        any(), org.mockito.ArgumentMatchers.anyInt(), any(Boolean.class), any());

        GmailConnectionService connectedConnectionService = mock(GmailConnectionService.class);
        GmailPreviewReadService revokedReadService = mock(GmailPreviewReadService.class);
        when(connectedConnectionService.currentStatus(TENANT_ID))
                .thenReturn(new GmailConnectionProjection("CONNECTED", "HEALTHY", null));
        when(revokedReadService.fetchRecentInboxMessages(
                        eq(TENANT_ID), eq(10), eq(false), eq(Duration.ofSeconds(5))))
                .thenThrow(
                        new GmailPreviewReadService.GmailPreviewReadUnavailableException(
                                GmailPreviewReadService.UnavailableReason.REVOKED));

        RulePreviewDataService revokedDataService =
                new RulePreviewDataService(connectedConnectionService, revokedReadService);

        assertThatThrownBy(
                        () ->
                                revokedDataService.fetchPreviewInputs(
                                        TENANT_ID,
                                        new MatcherNode.SenderDomainMatcher(
                                                "sender-domain", "stripe.com"),
                                        new PreviewSampleSize(10)))
                .isInstanceOf(GmailPreviewUnavailableException.class)
                .extracting("reason")
                .isEqualTo(GmailPreviewUnavailableException.Reason.REVOKED);
    }

    @Test
    void metadata_only_matchers_do_not_trigger_body_fetch_and_body_evidence_flag_does() {
        GmailConnectionService gmailConnectionService = mock(GmailConnectionService.class);
        GmailPreviewReadService gmailPreviewReadService = mock(GmailPreviewReadService.class);
        when(gmailConnectionService.currentStatus(TENANT_ID))
                .thenReturn(new GmailConnectionProjection("CONNECTED", "HEALTHY", null));
        when(gmailPreviewReadService.fetchRecentInboxMessages(
                        eq(TENANT_ID), eq(10), eq(false), eq(Duration.ofSeconds(5))))
                .thenReturn(List.of(gmailPreviewMessage()));
        when(gmailPreviewReadService.fetchRecentInboxMessages(
                        eq(TENANT_ID), eq(10), eq(true), eq(Duration.ofSeconds(5))))
                .thenReturn(List.of(gmailPreviewMessage()));
        RulePreviewDataService dataService =
                new RulePreviewDataService(gmailConnectionService, gmailPreviewReadService);

        dataService.fetchPreviewInputs(
                TENANT_ID,
                new MatcherNode.SubjectContainsMatcher("subject", "receipt"),
                new PreviewSampleSize(10));
        dataService.fetchPreviewInputs(TENANT_ID, true, new PreviewSampleSize(10));

        verify(gmailPreviewReadService)
                .fetchRecentInboxMessages(TENANT_ID, 10, false, Duration.ofSeconds(5));
        verify(gmailPreviewReadService)
                .fetchRecentInboxMessages(TENANT_ID, 10, true, Duration.ofSeconds(5));
    }

    @Test
    void repositories_do_not_accept_raw_preview_content_fields() throws Exception {
        Path productionRoot = findCoreProductionRoot();
        try (Stream<Path> sourcePaths = Files.walk(productionRoot.resolve("com/zeromail/core"))) {
            List<Path> repositorySources =
                    sourcePaths
                            .filter(Files::isRegularFile)
                            .filter(
                                    sourcePath ->
                                            sourcePath
                                                    .getFileName()
                                                    .toString()
                                                    .endsWith("Repository.java"))
                            .toList();
            for (Path repositorySource : repositorySources) {
                assertThat(
                                FORBIDDEN_REPOSITORY_CONTENT_NAME
                                        .matcher(Files.readString(repositorySource))
                                        .find())
                        .as("Repository %s must not accept raw preview content", repositorySource)
                        .isFalse();
            }
        }
    }

    @Test
    void preview_services_do_not_reference_gmail_write_apis() throws Exception {
        Path productionRoot = findCoreProductionRoot();
        List<Path> previewServiceSources;
        try (Stream<Path> sourcePaths = Files.walk(productionRoot.resolve("com/zeromail/core"))) {
            previewServiceSources =
                    sourcePaths
                            .filter(Files::isRegularFile)
                            .filter(
                                    sourcePath ->
                                            sourcePath.getFileName().toString().contains("Preview"))
                            .filter(
                                    sourcePath ->
                                            sourcePath.getFileName().toString().endsWith(".java"))
                            .toList();
        }

        for (Path previewServiceSource : previewServiceSources) {
            String sourceText = Files.readString(previewServiceSource);
            assertThat(sourceText)
                    .as("Preview source %s must not call Gmail write APIs", previewServiceSource)
                    .doesNotContain(".modify(", ".trash(", ".untrash(", ".send(", ".batchModify(")
                    .doesNotContain(".batchDelete(", ".delete(");
        }
    }

    private static GmailPreviewReadService.GmailPreviewMessage gmailPreviewMessage() {
        return new GmailPreviewReadService.GmailPreviewMessage(
                "gmail-1",
                "thread-1",
                "billing@stripe.com",
                "stripe.com",
                "Stripe Billing",
                List.of("founder@example.test"),
                List.<String>of(),
                "Receipt from Stripe",
                "<receipt@stripe.com>",
                "<thread-root@stripe.com>",
                "",
                "billing@stripe.com",
                List.of("INBOX"),
                List.<String>of(),
                Instant.parse("2026-05-09T10:00:00Z"),
                Instant.parse("2026-05-09T10:01:00Z"),
                false,
                false,
                null,
                null,
                false,
                false,
                Optional.<Boolean>empty(),
                Set.<String>of());
    }

    private static Path findCoreProductionRoot() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        while (currentDirectory != null) {
            Path monorepoCoreRoot = currentDirectory.resolve("backend/core/src/main/java");
            if (Files.isDirectory(monorepoCoreRoot)) {
                return monorepoCoreRoot;
            }
            Path moduleCoreRoot = currentDirectory.resolve("src/main/java");
            if (Files.isDirectory(moduleCoreRoot)
                    && "core".equals(currentDirectory.getFileName().toString())) {
                return moduleCoreRoot;
            }
            currentDirectory = currentDirectory.getParent();
        }
        throw new IllegalStateException("Could not locate backend/core/src/main/java");
    }
}
