package com.zeromail.api.security.testfixtures;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Best-effort stub of {@code https://oauth2.googleapis.com/revoke} for failure-handler
 * integration tests. Spec: {@code https://developers.google.com/identity/protocols/oauth2/web-server#tokenrevoke}.
 *
 * <p>Bound to localhost on an ephemeral port (per-test-class lifecycle). Captures the
 * {@code token} query parameter from each POST so tests can assert the failure handler
 * actually attempted revocation. Threat T-1.4-W0-02 (tampering): the server is reachable
 * only from inside the same JVM — no port forwarding, no credentials, no PII storage.
 *
 * <p>Wave 0 RED scaffold — compiles standalone (depends only on JDK). The
 * {@code GmailOAuthFailureHandler} that exercises this fixture lands in Wave 1 (Plan 02).
 *
 * <p><b>Privacy:</b> received tokens are kept in-memory for the duration of one test class
 * and never logged. Test code that prints them violates threat T-1.4-W0-01 — assert via
 * {@link #getReceivedTokens()} only.
 */
public final class MockGoogleRevocationServer {

    private final List<String> receivedTokens = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextResponseStatus = new AtomicInteger(200);

    private HttpServer server;

    /** Bind to ephemeral port and register the {@code /revoke} handler. */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/revoke", new RevokeHandler());
        server.start();
    }

    /** Bound port (call only after {@link #start()}). */
    public int port() {
        if (server == null) {
            throw new IllegalStateException("MockGoogleRevocationServer is not started");
        }
        return server.getAddress().getPort();
    }

    /** Graceful shutdown with zero-second delay. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** Defensive copy of every {@code token} query value seen so far. */
    public List<String> getReceivedTokens() {
        synchronized (receivedTokens) {
            return List.copyOf(receivedTokens);
        }
    }

    /** Clear the captured-token buffer between tests. */
    public void clearReceivedTokens() {
        receivedTokens.clear();
    }

    /**
     * One-shot override for the next request's HTTP status. Resets to 200 after one use,
     * so tests can simulate transient 5xx without affecting subsequent calls.
     */
    public void setNextResponseStatus(int status) {
        nextResponseStatus.set(status);
    }

    private final class RevokeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream body = exchange.getRequestBody()) {
                // Drain the request body to allow connection reuse; content is irrelevant.
                body.readAllBytes();
            }

            String token = parseTokenParam(exchange.getRequestURI(), readBodyAsString());
            if (token != null) {
                receivedTokens.add(token);
            }

            int status = nextResponseStatus.getAndSet(200);
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        }

        private String readBodyAsString() {
            // Body already drained above; this overload exists for the form-encoded path
            // (Google's spec accepts the token via either query param or body). Tests assert
            // the query-param shape, but the helper is here in case Wave 1 sends it as a body.
            return "";
        }

        private String parseTokenParam(URI uri, String formBody) {
            String query = uri.getRawQuery();
            String token = extractToken(query);
            if (token != null) return token;
            return extractToken(formBody);
        }

        private String extractToken(String encoded) {
            if (encoded == null || encoded.isEmpty()) return null;
            for (String pair : encoded.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                String name = pair.substring(0, eq);
                if ("token".equals(name)) {
                    return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }
}
