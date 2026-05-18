package com.zeromail.api.controllers.chat;

import com.zeromail.api.dto.chat.ChatStreamRequestDto;
import com.zeromail.core.chat.llm.VercelProtocolEmitter;
import com.zeromail.core.chat.usecases.ChatOrchestrator;
import com.zeromail.core.chat.usecases.ZeroMailChatProperties;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@RestController
@Tag(name = "Chat")
@RequestMapping("/api/chat")
@SuppressWarnings("UnknownHttpHeader")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String VERCEL_PROTOCOL_HEADER =
            String.join("-", "x", "vercel", "ai", "ui", "message", "stream");

    private final ChatOrchestrator chatOrchestrator;
    private final TaskScheduler chatHeartbeatTaskScheduler;
    private final ZeroMailChatProperties chatProperties;

    public ChatController(
            ChatOrchestrator chatOrchestrator,
            @Qualifier("chatHeartbeatTaskScheduler") TaskScheduler chatHeartbeatTaskScheduler,
            ZeroMailChatProperties chatProperties) {
        this.chatOrchestrator = chatOrchestrator;
        this.chatHeartbeatTaskScheduler = chatHeartbeatTaskScheduler;
        this.chatProperties = chatProperties;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @Valid @RequestBody ChatStreamRequestDto request, HttpServletResponse response) {
        String tenantId = TenantContext.currentOrThrow();
        response.setHeader(VERCEL_PROTOCOL_HEADER, "v1");
        SseEmitter sseEmitter =
                new SseEmitter(Duration.ofMinutes(chatProperties.sseTimeoutMinutes()).toMillis());
        AtomicReference<Disposable> streamSubscriptionReference = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> heartbeatFutureReference = new AtomicReference<>();
        AtomicBoolean cleanupStarted = new AtomicBoolean(false);
        Runnable cleanup =
                () -> {
                    if (!cleanupStarted.compareAndSet(false, true)) {
                        return;
                    }
                    Disposable streamSubscription = streamSubscriptionReference.get();
                    if (streamSubscription != null) {
                        streamSubscription.dispose();
                    }
                    ScheduledFuture<?> heartbeatFuture = heartbeatFutureReference.get();
                    if (heartbeatFuture != null) {
                        heartbeatFuture.cancel(false);
                    }
                };
        VercelProtocolEmitter vercelProtocolEmitter =
                new VercelProtocolEmitter(new SseEmitterFrameWriter(sseEmitter, cleanup));
        ScheduledFuture<?> heartbeatFuture =
                chatHeartbeatTaskScheduler.scheduleAtFixedRate(
                        vercelProtocolEmitter::emitHeartbeat,
                        Duration.ofSeconds(chatProperties.heartbeatIntervalSeconds()));
        heartbeatFutureReference.set(heartbeatFuture);
        Disposable streamSubscription =
                chatOrchestrator.stream(request.toCommand(tenantId), vercelProtocolEmitter);
        streamSubscriptionReference.set(streamSubscription);
        if (cleanupStarted.get()) {
            streamSubscription.dispose();
        }
        sseEmitter.onCompletion(cleanup);
        sseEmitter.onTimeout(
                () -> {
                    cleanup.run();
                    sseEmitter.complete();
                });
        sseEmitter.onError(
                throwable -> {
                    cleanup.run();
                    log.info(
                            "event=chat_stream_error tenantId={} reason={}",
                            tenantId,
                            throwable.getClass().getSimpleName());
                });
        return sseEmitter;
    }

    private record SseEmitterFrameWriter(SseEmitter sseEmitter, Runnable terminalFrameCleanup)
            implements VercelProtocolEmitter.FrameWriter {

        @Override
        public void write(String frame) throws IOException {
            if (frame.startsWith(": keepalive")) {
                sseEmitter.send(SseEmitter.event().comment("keepalive"));
                return;
            }
            sseEmitter.send(SseEmitter.event().data(frame));
            if (frame.contains("\"type\":\"finish\"") || frame.contains("\"type\":\"error\"")) {
                terminalFrameCleanup.run();
                sseEmitter.complete();
            }
        }
    }
}
