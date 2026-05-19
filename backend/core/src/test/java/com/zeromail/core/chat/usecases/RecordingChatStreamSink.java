package com.zeromail.core.chat.usecases;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class RecordingChatStreamSink implements ChatStreamSink {

    private final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
    private final CountDownLatch finishLatch = new CountDownLatch(1);

    List<String> events() {
        return List.copyOf(events);
    }

    boolean awaitFinish() throws InterruptedException {
        return finishLatch.await(10, TimeUnit.SECONDS);
    }

    @Override
    public void emitTextStart(String partId) {
        events.add("text-start:" + partId);
    }

    @Override
    public void emitTextDelta(String partId, String tokenText) {
        events.add("text-delta:" + tokenText);
    }

    @Override
    public void emitTextEnd(String partId) {
        events.add("text-end:" + partId);
    }

    @Override
    public void emitToolInputStart(String toolCallId, String toolName) {
        events.add("tool-input-start:" + toolName);
    }

    @Override
    public void emitToolInputAvailable(String toolCallId, String toolName, String inputJson) {
        events.add("tool-input-available:" + toolName + ":" + inputJson);
    }

    @Override
    public void emitToolOutputAvailable(String toolCallId, String outputJson) {
        events.add("tool-output-available:" + outputJson);
    }

    @Override
    public void emitDataPersistence(UUID chatMessageId, String state) {
        events.add("data-persistence:" + state);
    }

    @Override
    public void emitFinish(String reason) {
        events.add("finish:" + reason);
        finishLatch.countDown();
    }

    @Override
    public void emitError(String code, String userFacingMessage) {
        events.add("error:" + code);
        finishLatch.countDown();
    }

    @Override
    public void emitHeartbeat() {
        events.add("heartbeat");
    }
}
