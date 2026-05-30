package com.zeromail.api.security;

import com.zeromail.core.billing.config.BillingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

public class LemonSqueezyWebhookSignatureFilter extends OncePerRequestFilter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String WEBHOOK_PATH = "/api/plan-upgrades/webhooks/lemon-squeezy";

    private final BillingProperties.LemonSqueezyProperties lemonSqueezy;

    public LemonSqueezyWebhookSignatureFilter(BillingProperties billingProperties) {
        this.lemonSqueezy = billingProperties.lemonSqueezy();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !WEBHOOK_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        byte[] requestBody = request.getInputStream().readAllBytes();
        if (!signatureVerified(requestBody, request.getHeader(SIGNATURE_HEADER))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, requestBody), response);
    }

    private boolean signatureVerified(byte[] requestBody, String signatureHeader) {
        String signingSecret = lemonSqueezy.webhookSigningSecret();
        if (signingSecret == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String expectedSignature = hmacSha256Hex(signingSecret, requestBody);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(String signingSecret, byte[] requestBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(
                    new SecretKeySpec(
                            signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(requestBody));
        } catch (Exception hmacFailure) {
            return "";
        }
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] requestBody;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] requestBody) {
            super(request);
            this.requestBody = requestBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(requestBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Async request body reads are unused");
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String characterEncoding = getCharacterEncoding();
            Charset requestCharset =
                    characterEncoding == null
                            ? StandardCharsets.UTF_8
                            : Charset.forName(characterEncoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), requestCharset));
        }
    }
}
