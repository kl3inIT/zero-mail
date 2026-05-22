package com.zeromail.core.admin.mkey.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyRepository;
import com.zeromail.core.admin.mkey.persistence.lowlevel.LlmProviderMasterKeyWriteRepository;
import com.zeromail.core.admin.mkey.projection.MasterKeyMaskedRow;
import com.zeromail.core.llm.gateway.springai.admin.ModelsProbeClient;
import com.zeromail.core.llm.gateway.springai.admin.ProviderMasterKeyResolver;
import com.zeromail.core.shared.crypto.PlatformSecretCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Invariant: {@link MasterKeyAdminService#listMasked()} returns exactly one row per provider, and
 * within a provider it picks the row with a populated {@code maskedKey} (real key material) over a
 * row whose {@code maskedKey} is {@code null} (legacy pre-080 row or a never-keyed slot). This pins
 * the dedup behavior of the provider list card on the admin page.
 *
 * <p>Test slice: plain JUnit + Mockito on {@link ProviderMasterKeyResolver}. No DB needed — the
 * dedup happens entirely in Java once the resolver hands back a multi-row list. Other collaborators
 * on {@link MasterKeyAdminService} are unused by this code path; they are passed as mocks to
 * satisfy the constructor only.
 */
class MasterKeyAdminServiceListMaskedDedupTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-22T10:00:00Z");

    private static AdminUser admin() {
        return new AdminUser(
                UUID.fromString("00000000-0000-0000-0000-000000000fff"),
                "ops@zeromail.test",
                AdminStatus.ACTIVE,
                Optional.empty());
    }

    private static MasterKeyAdminService serviceWith(ProviderMasterKeyResolver resolver) {
        return new MasterKeyAdminService(
                mock(LlmProviderMasterKeyRepository.class),
                mock(LlmProviderMasterKeyWriteRepository.class),
                resolver,
                mock(PlatformSecretCipher.class),
                mock(MasterKeyEditSessionService.class),
                mock(MasterKeyRateLimiter.class),
                mock(ModelsProbeClient.class),
                mock(AdminAuditWriter.class),
                mock(ApplicationEventPublisher.class),
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName(
            "two rows for OPENROUTER (null mask + populated mask) collapse to the populated mask")
    void dedup_prefers_row_with_populated_masked_key() {
        ProviderMasterKeyResolver resolver = mock(ProviderMasterKeyResolver.class);
        MasterKeyMaskedRow nullKeyRow =
                new MasterKeyMaskedRow(
                        LlmProvider.OPENROUTER,
                        null,
                        KeyFormat.OPENAI_FORMAT,
                        (short) 1,
                        1L,
                        FIXED_INSTANT,
                        MasterKeyMaskedRow.NO_DEPENDENTS,
                        false,
                        "https://openrouter.ai/api/v1",
                        false,
                        false,
                        false);
        MasterKeyMaskedRow populatedKeyRow =
                new MasterKeyMaskedRow(
                        LlmProvider.OPENROUTER,
                        "sk-...abcd",
                        KeyFormat.OPENAI_FORMAT,
                        (short) 1,
                        1L,
                        FIXED_INSTANT,
                        MasterKeyMaskedRow.NO_DEPENDENTS,
                        false,
                        "https://openrouter.ai/api/v1",
                        false,
                        false,
                        false);
        when(resolver.maskedRows()).thenReturn(List.of(nullKeyRow, populatedKeyRow));

        MasterKeyAdminService service = serviceWith(resolver);
        List<MasterKeyMaskedRow> rows = AdminContext.run(admin(), () -> service.listMasked());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).provider()).isEqualTo(LlmProvider.OPENROUTER);
        assertThat(rows.get(0).maskedKey()).isEqualTo("sk-...abcd");
    }

    @Nested
    @DisplayName("ProviderWithNoKeyAtAll")
    class ProviderWithNoKeyAtAll {

        @Test
        @DisplayName(
                "provider returned by resolver with zero keys still surfaces with null mask and"
                        + " NO_DEPENDENTS")
        void provider_with_no_keys_still_appears_in_list() {
            // The resolver's contract today is one row per existing (provider, key); a provider
            // with zero key rows yields zero entries in maskedRows(). The list-page UI surfaces
            // a "no key" card by other means (the union of catalog-known providers vs returned
            // rows). For the dedup invariant we pin: when the resolver DOES emit a single
            // null-mask row for a provider (legacy unkeyed seed row from changelog 058), the
            // dedup logic preserves it rather than dropping it.
            ProviderMasterKeyResolver resolver = mock(ProviderMasterKeyResolver.class);
            MasterKeyMaskedRow unkeyedSeedRow =
                    new MasterKeyMaskedRow(
                            LlmProvider.ANTHROPIC,
                            null,
                            null,
                            null,
                            0L,
                            null,
                            MasterKeyMaskedRow.NO_DEPENDENTS,
                            false,
                            "https://api.anthropic.com/v1",
                            false,
                            false,
                            false);
            when(resolver.maskedRows()).thenReturn(List.of(unkeyedSeedRow));

            MasterKeyAdminService service = serviceWith(resolver);
            List<MasterKeyMaskedRow> rows = AdminContext.run(admin(), () -> service.listMasked());

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).provider()).isEqualTo(LlmProvider.ANTHROPIC);
            assertThat(rows.get(0).maskedKey()).isNull();
            assertThat(rows.get(0).dependentsCount()).isEqualTo(MasterKeyMaskedRow.NO_DEPENDENTS);
        }
    }
}
