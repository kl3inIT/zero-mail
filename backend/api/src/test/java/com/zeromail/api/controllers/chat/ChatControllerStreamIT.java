package com.zeromail.api.controllers.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.chat.usecases.ChatOrchestrator;
import com.zeromail.core.chat.usecases.ChatStreamCommand;
import com.zeromail.core.chat.usecases.ChatStreamSink;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import reactor.core.Disposable;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class ChatControllerStreamIT extends ApiPostgresTestBase {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;
    @Autowired ObjectMapper objectMapper;
    @Autowired Environment environment;
    @Autowired WebApplicationContext webApplicationContext;

    @MockitoBean ChatOrchestrator chatOrchestrator;

    @MockitoBean(name = "chatHeartbeatTaskScheduler")
    TaskScheduler chatHeartbeatTaskScheduler;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc =
                MockMvcBuilders.webAppContextSetup(webApplicationContext)
                        .apply(springSecurity())
                        .build();
    }

    @Test
    void stream_endpoint_sets_vercel_header_streams_frames_and_cleans_up_lifecycle()
            throws Exception {
        SeedData seedData = seedUser();
        RecordingDisposable streamSubscription = new RecordingDisposable();
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> heartbeatFuture = mock(ScheduledFuture.class);
        doReturn(heartbeatFuture)
                .when(chatHeartbeatTaskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        when(chatOrchestrator.stream(any(ChatStreamCommand.class), any(ChatStreamSink.class)))
                .thenAnswer(
                        invocation -> {
                            ChatStreamSink streamSink = invocation.getArgument(1);
                            CompletableFuture.runAsync(
                                    () -> {
                                        streamSink.emitTextStart("assistant-text");
                                        streamSink.emitTextDelta("assistant-text", "Xin ");
                                        streamSink.emitTextDelta("assistant-text", "chào");
                                        streamSink.emitTextEnd("assistant-text");
                                        streamSink.emitFinish("complete");
                                    });
                            return streamSubscription;
                        });

        MvcResult mvcResult =
                mockMvc.perform(
                                post("/api/chat")
                                        .header(
                                                TestSessionSupport.HEADER_SUBJECT,
                                                seedData.googleSubject())
                                        .header(TestSessionSupport.HEADER_EMAIL, seedData.email())
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        java.util.Map.of(
                                                                "chatId",
                                                                UUID.randomUUID(),
                                                                "userText",
                                                                "Xin chào Zero Mail"))))
                        .andExpect(request().asyncStarted())
                        .andReturn();

        MvcResult dispatchedResult =
                mockMvc.perform(asyncDispatch(mvcResult))
                        .andExpect(status().isOk())
                        .andExpect(header().string("x-vercel-ai-ui-message-stream", "v1"))
                        .andReturn();

        assertThat(dispatchedResult.getResponse().getContentAsString())
                .contains("\"type\":\"text-delta\"")
                .contains("\"delta\":\"Xin \"")
                .contains("\"type\":\"finish\"");
        assertThat(streamSubscription.awaitDisposed()).isTrue();
        verify(heartbeatFuture, timeout(1000)).cancel(false);
        assertThat(environment.getProperty("spring.ai.chat.observations.log-prompt", Boolean.class))
                .isFalse();
        assertThat(
                        environment.getProperty(
                                "spring.ai.chat.observations.log-completion", Boolean.class))
                .isFalse();
        assertThat(
                        environment.getProperty(
                                "spring.ai.openai.chat.observations.include-completion",
                                Boolean.class))
                .isFalse();
    }

    private SeedData seedUser() {
        String label = "chat-stream";
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, label));
        String googleSubject = "sub-" + label;
        String email = label + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                userRepository.save(
                                        new UserEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                googleSubject,
                                                email)));
        testSessionMinter.mint(googleSubject, email);
        return new SeedData(tenantId, googleSubject, email);
    }

    private record SeedData(UUID tenantId, String googleSubject, String email) {}

    private static final class RecordingDisposable implements Disposable {

        private final CountDownLatch disposedLatch = new CountDownLatch(1);

        boolean awaitDisposed() throws InterruptedException {
            return disposedLatch.await(5, TimeUnit.SECONDS);
        }

        @Override
        public void dispose() {
            disposedLatch.countDown();
        }
    }
}
