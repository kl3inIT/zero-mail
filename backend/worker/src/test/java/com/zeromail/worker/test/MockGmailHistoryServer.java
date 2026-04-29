package com.zeromail.worker.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class MockGmailHistoryServer implements AutoCloseable {

    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private HttpServer server;
    private volatile String lastWatchRequestBody;

    public record HistoryMessageResponse(
            String messageId,
            String threadId,
            List<String> labelIds,
            Long internalDate) {}

    public void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::dispatch);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Override
    public void close() {
        stop();
    }

    public String baseUrl() {
        if (server == null) {
            throw new IllegalStateException("MockGmailHistoryServer must be started before baseUrl()");
        }
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    public String lastWatchRequestBody() {
        return lastWatchRequestBody;
    }

    public void reset() {
        responses.clear();
        lastWatchRequestBody = null;
    }

    public void stubTokenSuccess() {
        responses.put("/token",
                new Response(200, "{\"access_token\":\"test-access-token\",\"expires_in\":3600}"));
    }

    public void stubTokenInvalidGrant() {
        responses.put("/token",
                new Response(400, "{\"error\":\"invalid_grant\"}"));
    }

    public void stubHistoryList(long startHistoryId, List<HistoryMessageResponse> messages) {
        responses.put("/gmail/v1/users/me/history", new Response(200, historyResponse(startHistoryId, messages)));
    }

    public void stubHistoryList404() {
        responses.put("/gmail/v1/users/me/history", new Response(404, "{\"error\":{\"code\":404}}"));
    }

    public void stubMessageMetadata(String messageId, String threadId, List<String> labelIds, Long internalDate) {
        responses.put("/gmail/v1/users/me/messages/" + messageId,
                new Response(200, messageResponse(messageId, threadId, labelIds, internalDate)));
    }

    public void stubWatchSuccess(long historyId, long expirationMs) {
        responses.put("/gmail/v1/users/me/watch",
                new Response(200, "{\"historyId\":\"" + historyId + "\",\"expiration\":\"" + expirationMs + "\"}"));
    }

    public void stubWatchFailure(int statusCode) {
        responses.put("/gmail/v1/users/me/watch",
                new Response(statusCode, "{\"error\":{\"code\":" + statusCode + "}}"));
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/watch")) {
            lastWatchRequestBody = new String(readRequestBody(exchange), StandardCharsets.UTF_8);
        }
        Response response = responses.getOrDefault(path, new Response(404, "{\"error\":{\"code\":404}}"));
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] readRequestBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
        if ("gzip".equalsIgnoreCase(encoding)) {
            in = new GZIPInputStream(in);
        }
        return in.readAllBytes();
    }

    private static String historyResponse(long startHistoryId, List<HistoryMessageResponse> messages) {
        List<String> added = new ArrayList<>();
        for (HistoryMessageResponse message : messages) {
            added.add("{\"message\":" + messageShape(message.messageId(), message.threadId(), message.labelIds(), null) + "}");
        }
        return "{\"history\":[{\"id\":\"" + startHistoryId + "\",\"messagesAdded\":["
                + String.join(",", added)
                + "]}],\"historyId\":\"" + startHistoryId + "\"}";
    }

    private static String messageResponse(String messageId, String threadId, List<String> labelIds, Long internalDate) {
        return messageShape(messageId, threadId, labelIds, internalDate);
    }

    private static String messageShape(String messageId, String threadId, List<String> labelIds, Long internalDate) {
        StringBuilder json = new StringBuilder();
        json.append("{\"id\":\"").append(escape(messageId)).append("\",")
                .append("\"threadId\":\"").append(escape(threadId)).append("\",")
                .append("\"labelIds\":").append(labels(labelIds));
        if (internalDate != null) {
            json.append(",\"internalDate\":\"").append(internalDate).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    private static String labels(List<String> labelIds) {
        return labelIds.stream()
                .map(label -> "\"" + escape(label) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Response(int status, String body) {}
}
