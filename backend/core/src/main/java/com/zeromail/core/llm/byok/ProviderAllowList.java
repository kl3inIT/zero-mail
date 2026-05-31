package com.zeromail.core.llm.byok;

import com.zeromail.core.llm.domain.LlmProvider;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProviderAllowList {

    private static final Set<LlmProvider> USER_BYOK_ALLOWED_PROVIDERS =
            Set.of(
                    LlmProvider.OPENAI,
                    LlmProvider.ANTHROPIC,
                    LlmProvider.GOOGLE,
                    LlmProvider.DEEPSEEK);

    public LlmProvider validateForByok(String providerId) {
        LlmProvider provider = LlmProvider.fromId(providerId);
        if (!USER_BYOK_ALLOWED_PROVIDERS.contains(provider)) {
            throw new ProviderNotAllowedException();
        }
        return provider;
    }

    public static class ProviderNotAllowedException extends BusinessException {

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "ai.byok.provider_not_allowed";
        }

        @Override
        public String logEvent() {
            return "byok_provider_not_allowed";
        }

        @Override
        public String title() {
            return "BYOK provider not allowed";
        }

        @Override
        public String detail() {
            return "The selected provider cannot be used for user BYOK.";
        }
    }
}
