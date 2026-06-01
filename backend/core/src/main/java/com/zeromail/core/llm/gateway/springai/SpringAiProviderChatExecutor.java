package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmProviderChatExecutor;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

@Component
public class SpringAiProviderChatExecutor implements LlmProviderChatExecutor {

    private final SpringAiProviderChatClientFactory chatClientFactory;
    private final SpringAiByokChatSupport chatSupport = new SpringAiByokChatSupport();

    public SpringAiProviderChatExecutor(SpringAiProviderChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    @Override
    public LlmChatResult call(LlmProviderCredential credential, LlmChatRequest request) {
        try {
            ChatClient chatClient =
                    chatClientFactory.create(
                            credential,
                            request.model(),
                            request.temperature(),
                            request.maxTokens(),
                            request.toolChoiceRequired());
            ChatClient.ChatClientRequestSpec requestSpecification =
                    chatClient.prompt().system(request.systemPrompt()).user(request.userMessage());
            if (!request.tools().isEmpty()) {
                requestSpecification =
                        requestSpecification.tools(
                                toolSpec ->
                                        toolSpec.callbacks(
                                                chatSupport.translateTools(request.tools())));
            }
            ChatResponse chatResponse =
                    requestSpecification
                            .advisors(SpringAiRawToolCallSupport::preserveRawToolCalls)
                            .options(
                                    chatClientFactory.options(
                                            credential,
                                            request.model(),
                                            request.temperature(),
                                            request.maxTokens(),
                                            request.toolChoiceRequired()))
                            .call()
                            .chatResponse();
            if (chatResponse == null) {
                throw new IllegalStateException("No chat response returned");
            }
            return chatSupport.toLlmChatResult(chatResponse);
        } finally {
            credential.wipe();
        }
    }
}
