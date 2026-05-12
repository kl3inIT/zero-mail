package com.zeromail.api.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;

public class MockGoogleOidcServer implements AutoCloseable {

    private static final String KEY_ID = "test-key-1";

    private final KeyPair signingKey;
    private HttpServer server;

    public MockGoogleOidcServer() {
        this.signingKey = generateKeyPair();
    }

    public void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", this::serveJwks);
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

    public String jwksUrl() {
        if (server == null) {
            throw new IllegalStateException(
                    "MockGoogleOidcServer must be started before jwksUrl()");
        }
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
    }

    public String sign(String audience, String email, String issuer, long expiresInSeconds) {
        long now = Instant.now().getEpochSecond();
        String payload =
                "{"
                        + "\"iss\":\""
                        + json(issuer)
                        + "\","
                        + "\"aud\":\""
                        + json(audience)
                        + "\","
                        + "\"email\":\""
                        + json(email)
                        + "\","
                        + "\"email_verified\":true,"
                        + "\"sub\":\"pubsub-test-subject\","
                        + "\"iat\":"
                        + now
                        + ","
                        + "\"exp\":"
                        + (now + expiresInSeconds)
                        + "}";
        return signPayload(payload, signingKey.getPrivate(), KEY_ID);
    }

    public String signWithWrongKey(String audience, String email) {
        long now = Instant.now().getEpochSecond();
        String payload =
                "{"
                        + "\"iss\":\"https://accounts.google.com\","
                        + "\"aud\":\""
                        + json(audience)
                        + "\","
                        + "\"email\":\""
                        + json(email)
                        + "\","
                        + "\"email_verified\":true,"
                        + "\"sub\":\"pubsub-test-subject\","
                        + "\"iat\":"
                        + now
                        + ","
                        + "\"exp\":"
                        + (now + 300)
                        + "}";
        return signPayload(payload, generateKeyPair().getPrivate(), "wrong-key");
    }

    private void serveJwks(HttpExchange exchange) throws IOException {
        byte[] body = jwks().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String jwks() {
        RSAPublicKey publicKey = (RSAPublicKey) signingKey.getPublic();
        return "{\"keys\":[{"
                + "\"kty\":\"RSA\","
                + "\"kid\":\""
                + KEY_ID
                + "\","
                + "\"use\":\"sig\","
                + "\"alg\":\"RS256\","
                + "\"n\":\""
                + base64Url(unsigned(publicKey.getModulus()))
                + "\","
                + "\"e\":\""
                + base64Url(unsigned(publicKey.getPublicExponent()))
                + "\""
                + "}]}";
    }

    private static String signPayload(String payloadJson, PrivateKey privateKey, String kid) {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
        String signingInput =
                base64Url(header.getBytes(StandardCharsets.UTF_8))
                        + "."
                        + base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + base64Url(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign synthetic OIDC token", e);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create RSA keypair", e);
        }
    }

    private static byte[] unsigned(BigInteger integer) {
        byte[] bytes = integer.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
