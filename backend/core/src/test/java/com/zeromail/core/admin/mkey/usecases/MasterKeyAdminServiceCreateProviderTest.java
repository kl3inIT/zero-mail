package com.zeromail.core.admin.mkey.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.cat.persistence.lowlevel.ProviderCatalogWriteRepository;
import com.zeromail.core.admin.cat.persistence.lowlevel.ProviderCatalogWriteRepository.ProviderDeleteCandidate;
import com.zeromail.core.admin.cat.persistence.lowlevel.ProviderCatalogWriteRepository.ProviderDeleteResult;
import com.zeromail.core.admin.mkey.domain.MasterKeyStatus;
import com.zeromail.core.admin.mkey.exception.MasterKeyTestFailedException;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyRepository;
import com.zeromail.core.admin.mkey.persistence.lowlevel.LlmProviderMasterKeyWriteRepository;
import com.zeromail.core.llm.domain.KeyFormat;
import com.zeromail.core.llm.domain.LlmProvider;
import com.zeromail.core.llm.domain.MasterKeyTestResult;
import com.zeromail.core.llm.gateway.springai.ConnectionTestResult;
import com.zeromail.core.llm.gateway.springai.ProviderConnectionTester;
import com.zeromail.core.shared.crypto.PlatformSecretCipher;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MasterKeyAdminServiceCreateProviderTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-22T10:00:00Z");

    private static AdminUser admin() {
        return new AdminUser(
                UUID.fromString("00000000-0000-0000-0000-000000000fff"),
                "ops@zeromail.test",
                AdminStatus.ACTIVE,
                Optional.empty());
    }

    @Test
    @DisplayName("createCompatibleProvider probes before inserting provider catalog and key rows")
    void create_provider_persists_only_after_successful_probe() {
        LlmProvider provider = LlmProvider.fromId("GATEWAY_TEST");
        UUID adminUserId = admin().id();
        ProviderMasterKeyResolver resolver = mock(ProviderMasterKeyResolver.class);
        LlmProviderMasterKeyWriteRepository writeRepository =
                mock(LlmProviderMasterKeyWriteRepository.class);
        ProviderCatalogWriteRepository providerCatalogWriteRepository =
                mock(ProviderCatalogWriteRepository.class);
        PlatformSecretCipher platformSecretCipher = mock(PlatformSecretCipher.class);
        MasterKeyEditSessionService editSessionService = mock(MasterKeyEditSessionService.class);
        MasterKeyRateLimiter rateLimiter = mock(MasterKeyRateLimiter.class);
        ProviderConnectionTester providerConnectionTester = mock(ProviderConnectionTester.class);
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        byte[] plaintextKey = "sk-provider-test-key".getBytes(StandardCharsets.UTF_8);
        byte[] encryptedEnvelope = {0, 0, 0, 7, 1, 2, 3, 4};
        when(providerCatalogWriteRepository.exists(provider)).thenReturn(false);
        when(editSessionService.consume(adminUserId, provider, "edit-token"))
                .thenReturn(Optional.of("edit-token"));
        when(providerConnectionTester.probeConnection(
                        eq(provider),
                        eq(KeyFormat.OPENAI_FORMAT),
                        eq("https://gateway.example.com/v1"),
                        any(byte[].class)))
                .thenReturn(new ConnectionTestResult(MasterKeyTestResult.OK, List.of()));
        when(platformSecretCipher.encrypt(
                        any(byte[].class), eq("platform:master_key:GATEWAY_TEST")))
                .thenReturn(encryptedEnvelope);

        MasterKeyAdminService service =
                serviceWith(
                        resolver,
                        writeRepository,
                        providerCatalogWriteRepository,
                        platformSecretCipher,
                        editSessionService,
                        rateLimiter,
                        providerConnectionTester,
                        adminAuditWriter);

        MasterKeyAdminService.ProviderKeyAddResult result =
                AdminContext.run(
                        admin(),
                        () ->
                                service.createCompatibleProvider(
                                        "gateway_test",
                                        " Gateway Test ",
                                        KeyFormat.OPENAI_FORMAT,
                                        " https://gateway.example.com/v1 ",
                                        plaintextKey,
                                        "primary",
                                        "edit-token",
                                        "127.0.0.1",
                                        UUID.fromString("00000000-0000-0000-0000-000000000456")));

        assertThat(result.priority()).isEqualTo(1);
        verify(providerConnectionTester)
                .probeConnection(
                        eq(provider),
                        eq(KeyFormat.OPENAI_FORMAT),
                        eq("https://gateway.example.com/v1"),
                        any(byte[].class));
        verify(providerCatalogWriteRepository)
                .insertCompatibleGateway(
                        provider,
                        "Gateway Test",
                        KeyFormat.OPENAI_FORMAT,
                        "https://gateway.example.com/v1");
        verify(writeRepository)
                .insertKey(
                        eq(provider),
                        eq(result.keyId()),
                        eq(1),
                        eq(MasterKeyStatus.ACTIVE),
                        eq("primary"),
                        eq(KeyFormat.OPENAI_FORMAT),
                        eq(encryptedEnvelope),
                        eq((short) 7),
                        eq(adminUserId),
                        eq(FIXED_INSTANT),
                        eq("https://gateway.example.com/v1"),
                        eq("sk-****-key"));
        verify(resolver).invalidate(provider);
    }

    @Test
    @DisplayName("createCompatibleProvider rejects failed probe before inserting anything")
    void create_provider_does_not_persist_when_probe_fails() {
        LlmProvider provider = LlmProvider.fromId("GATEWAY_TEST_FAIL");
        UUID adminUserId = admin().id();
        ProviderMasterKeyResolver resolver = mock(ProviderMasterKeyResolver.class);
        LlmProviderMasterKeyWriteRepository writeRepository =
                mock(LlmProviderMasterKeyWriteRepository.class);
        ProviderCatalogWriteRepository providerCatalogWriteRepository =
                mock(ProviderCatalogWriteRepository.class);
        PlatformSecretCipher platformSecretCipher = mock(PlatformSecretCipher.class);
        MasterKeyEditSessionService editSessionService = mock(MasterKeyEditSessionService.class);
        MasterKeyRateLimiter rateLimiter = mock(MasterKeyRateLimiter.class);
        ProviderConnectionTester providerConnectionTester = mock(ProviderConnectionTester.class);
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        when(providerCatalogWriteRepository.exists(provider)).thenReturn(false);
        when(editSessionService.consume(adminUserId, provider, "edit-token"))
                .thenReturn(Optional.of("edit-token"));
        when(providerConnectionTester.probeConnection(
                        eq(provider),
                        eq(KeyFormat.ANTHROPIC_FORMAT),
                        eq("https://gateway.example.com/anthropic"),
                        any(byte[].class)))
                .thenReturn(new ConnectionTestResult(MasterKeyTestResult.INVALID_KEY, List.of()));

        MasterKeyAdminService service =
                serviceWith(
                        resolver,
                        writeRepository,
                        providerCatalogWriteRepository,
                        platformSecretCipher,
                        editSessionService,
                        rateLimiter,
                        providerConnectionTester,
                        adminAuditWriter);

        assertThatThrownBy(
                        () ->
                                AdminContext.run(
                                        admin(),
                                        () ->
                                                service.createCompatibleProvider(
                                                        "gateway_test_fail",
                                                        "Gateway Test Fail",
                                                        KeyFormat.ANTHROPIC_FORMAT,
                                                        "https://gateway.example.com/anthropic",
                                                        "sk-invalid"
                                                                .getBytes(StandardCharsets.UTF_8),
                                                        "primary",
                                                        "edit-token",
                                                        "127.0.0.1",
                                                        UUID.fromString(
                                                                "00000000-0000-0000-0000-000000000457"))))
                .isInstanceOf(MasterKeyTestFailedException.class);

        verify(providerCatalogWriteRepository, never())
                .insertCompatibleGateway(any(), any(), any(), any());
        verify(writeRepository, never())
                .insertKey(
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyShort(),
                        any(),
                        any(),
                        any(),
                        any());
        verify(platformSecretCipher, never()).encrypt(any(byte[].class), any());
        verify(resolver, never()).invalidate(provider);
    }

    @Test
    @DisplayName("deleteCompatibleProvider deletes only unused compatible gateway providers")
    void delete_provider_removes_unused_compatible_gateway() {
        LlmProvider provider = LlmProvider.fromId("GATEWAY_DELETE");
        ProviderMasterKeyResolver resolver = mock(ProviderMasterKeyResolver.class);
        LlmProviderMasterKeyWriteRepository writeRepository =
                mock(LlmProviderMasterKeyWriteRepository.class);
        ProviderCatalogWriteRepository providerCatalogWriteRepository =
                mock(ProviderCatalogWriteRepository.class);
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        when(providerCatalogWriteRepository.findDeleteCandidateOrNull(provider))
                .thenReturn(
                        new ProviderDeleteCandidate(
                                provider, "Gateway Delete", "COMPATIBLE_GATEWAY", "OPENAI_FORMAT"));
        when(providerCatalogWriteRepository.countRoutingReferences(provider)).thenReturn(0L);
        when(providerCatalogWriteRepository.countPinnedTenants(provider)).thenReturn(0L);
        when(providerCatalogWriteRepository.deleteCompatibleGateway(provider))
                .thenReturn(new ProviderDeleteResult(1, 2, 1));

        MasterKeyAdminService service =
                serviceWith(
                        resolver,
                        writeRepository,
                        providerCatalogWriteRepository,
                        mock(PlatformSecretCipher.class),
                        mock(MasterKeyEditSessionService.class),
                        mock(MasterKeyRateLimiter.class),
                        mock(ProviderConnectionTester.class),
                        adminAuditWriter);

        AdminContext.run(
                admin(),
                () ->
                        service.deleteCompatibleProvider(
                                provider,
                                "127.0.0.1",
                                UUID.fromString("00000000-0000-0000-0000-000000000458")));

        verify(providerCatalogWriteRepository).deleteCompatibleGateway(provider);
        verify(resolver).invalidate(provider);
        verify(adminAuditWriter)
                .append(
                        eq(AdminAuditAction.MASTER_KEY_SET),
                        eq("provider_catalog"),
                        isNull(),
                        isNull(),
                        argThat(
                                (Map<String, ?> afterState) ->
                                        provider.id().equals(afterState.get("provider"))
                                                && "DELETE_COMPATIBLE_PROVIDER"
                                                        .equals(afterState.get("action"))
                                                && Integer.valueOf(2)
                                                        .equals(afterState.get("deleted_models"))
                                                && Integer.valueOf(1)
                                                        .equals(afterState.get("deleted_keys"))),
                        eq("Deleted compatible LLM provider"),
                        eq("127.0.0.1"),
                        eq(UUID.fromString("00000000-0000-0000-0000-000000000458")));
    }

    @Test
    @DisplayName("deleteCompatibleProvider rejects Spring AI built-in providers")
    void delete_provider_rejects_built_in_provider() {
        LlmProvider provider = LlmProvider.OPENAI;
        ProviderCatalogWriteRepository providerCatalogWriteRepository =
                mock(ProviderCatalogWriteRepository.class);
        when(providerCatalogWriteRepository.findDeleteCandidateOrNull(provider))
                .thenReturn(
                        new ProviderDeleteCandidate(
                                provider, "OpenAI", "SPRING_AI_BUILT_IN", "OPENAI_FORMAT"));
        MasterKeyAdminService service =
                serviceWith(
                        mock(ProviderMasterKeyResolver.class),
                        mock(LlmProviderMasterKeyWriteRepository.class),
                        providerCatalogWriteRepository,
                        mock(PlatformSecretCipher.class),
                        mock(MasterKeyEditSessionService.class),
                        mock(MasterKeyRateLimiter.class),
                        mock(ProviderConnectionTester.class),
                        mock(AdminAuditWriter.class));

        assertThatThrownBy(
                        () ->
                                AdminContext.run(
                                        admin(),
                                        () ->
                                                service.deleteCompatibleProvider(
                                                        provider,
                                                        "127.0.0.1",
                                                        UUID.fromString(
                                                                "00000000-0000-0000-0000-000000000459"))))
                .isInstanceOf(MasterKeyAdminService.ProviderDeleteNotAllowedException.class);

        verify(providerCatalogWriteRepository, never()).deleteCompatibleGateway(provider);
    }

    @Test
    @DisplayName("deleteCompatibleProvider blocks providers still referenced by routing")
    void delete_provider_blocks_routing_references() {
        LlmProvider provider = LlmProvider.fromId("GATEWAY_IN_USE");
        ProviderCatalogWriteRepository providerCatalogWriteRepository =
                mock(ProviderCatalogWriteRepository.class);
        when(providerCatalogWriteRepository.findDeleteCandidateOrNull(provider))
                .thenReturn(
                        new ProviderDeleteCandidate(
                                provider,
                                "Gateway In Use",
                                "COMPATIBLE_GATEWAY",
                                "ANTHROPIC_FORMAT"));
        when(providerCatalogWriteRepository.countRoutingReferences(provider)).thenReturn(1L);
        when(providerCatalogWriteRepository.countPinnedTenants(provider)).thenReturn(0L);
        MasterKeyAdminService service =
                serviceWith(
                        mock(ProviderMasterKeyResolver.class),
                        mock(LlmProviderMasterKeyWriteRepository.class),
                        providerCatalogWriteRepository,
                        mock(PlatformSecretCipher.class),
                        mock(MasterKeyEditSessionService.class),
                        mock(MasterKeyRateLimiter.class),
                        mock(ProviderConnectionTester.class),
                        mock(AdminAuditWriter.class));

        assertThatThrownBy(
                        () ->
                                AdminContext.run(
                                        admin(),
                                        () ->
                                                service.deleteCompatibleProvider(
                                                        provider,
                                                        "127.0.0.1",
                                                        UUID.fromString(
                                                                "00000000-0000-0000-0000-000000000460"))))
                .isInstanceOf(MasterKeyAdminService.ProviderDeleteBlockedException.class);

        verify(providerCatalogWriteRepository, never()).deleteCompatibleGateway(provider);
    }

    private static MasterKeyAdminService serviceWith(
            ProviderMasterKeyResolver resolver,
            LlmProviderMasterKeyWriteRepository writeRepository,
            ProviderCatalogWriteRepository providerCatalogWriteRepository,
            PlatformSecretCipher platformSecretCipher,
            MasterKeyEditSessionService editSessionService,
            MasterKeyRateLimiter rateLimiter,
            ProviderConnectionTester providerConnectionTester,
            AdminAuditWriter adminAuditWriter) {
        return new MasterKeyAdminService(
                mock(LlmProviderMasterKeyRepository.class),
                writeRepository,
                providerCatalogWriteRepository,
                resolver,
                platformSecretCipher,
                editSessionService,
                rateLimiter,
                providerConnectionTester,
                adminAuditWriter,
                mock(ApplicationEventPublisher.class),
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }
}
