package com.zeromail.worker.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.chat.usecases.settings.AssistantDraftSettingsService;
import com.zeromail.core.draft.domain.ToneContext;
import com.zeromail.core.draft.usecases.DraftBodyGenerator;
import com.zeromail.core.draft.usecases.ToneContextBuilder;
import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.llm.usecases.ToolCallResult;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DraftSignatureIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000090203");

    @Test
    void generatedDraftIncludesSavedEmailSignature() throws Exception {
        String emailSignature = "Best regards,\nZero Mail";
        ToneContextBuilder toneContextBuilder = mock(ToneContextBuilder.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        AssistantDraftSettingsService assistantDraftSettingsService =
                mock(AssistantDraftSettingsService.class);
        given(toneContextBuilder.buildForCurrentTenant()).willReturn(ToneContext.empty());
        given(assistantDraftSettingsService.emailSignature(TENANT_ID))
                .willReturn(Optional.of(emailSignature));
        given(
                        llmGateway.chatForDraft(
                                eq(CallSite.DRAFT),
                                any(SanitizationContext.class),
                                eq(""),
                                eq(List.of()),
                                eq("Inbound subject")))
                .willReturn(
                        new ToolCallResult(
                                Action.SAVE_DRAFT, Map.of("body", "Generated draft body")));
        DraftBodyGenerator draftBodyGenerator =
                new DraftBodyGenerator(
                        new SanitizationPipeline(List.of(passThroughSanitizer())),
                        toneContextBuilder,
                        llmGateway,
                        assistantDraftSettingsService);

        String generatedDraftBody =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(
                                () ->
                                        draftBodyGenerator.generate(
                                                TENANT_ID,
                                                "gmail-thread-signature",
                                                "Inbound body",
                                                "Inbound subject"));

        assertThat(generatedDraftBody)
                .isEqualTo("Generated draft body\n\n" + emailSignature)
                .endsWith(emailSignature);
    }

    private static Sanitizer passThroughSanitizer() {
        return sanitizationContext -> sanitizationContext.withTokenCount(5, false);
    }
}
