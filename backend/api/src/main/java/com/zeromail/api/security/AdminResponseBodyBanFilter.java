package com.zeromail.api.security;

import com.zeromail.core.admin.audit.AdminBodyBanRegex;
import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

@Component
public class AdminResponseBodyBanFilter extends OncePerRequestFilter {

    private static final int MAX_ALLOWED_FORBIDDEN_STRING_LENGTH = 200;
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final AdminAuditWriter adminAuditWriter;

    public AdminResponseBodyBanFilter(AdminAuditWriter adminAuditWriter) {
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, responseWrapper);
        byte[] responseBody = responseWrapper.getContentAsByteArray();
        if (isJson(responseWrapper.getContentType()) && containsForbiddenBodyField(responseBody)) {
            UUID auditId =
                    adminAuditWriter.appendAsSystem(
                            AdminAuditAction.ADMIN_RESPONSE_BODY_BAN_TRIPPED,
                            "ADMIN_RESPONSE",
                            null,
                            null,
                            "{\"path\":\"" + escapeJson(request.getRequestURI()) + "\"}",
                            "Admin response body ban tripped",
                            request.getRemoteAddr(),
                            UUID.randomUUID());
            responseWrapper.resetBuffer();
            responseWrapper.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseWrapper.setContentType(MediaType.APPLICATION_JSON_VALUE);
            responseWrapper.setCharacterEncoding(StandardCharsets.UTF_8.name());
            responseWrapper
                    .getWriter()
                    .write("{\"code\":\"error.admin.body_ban\",\"auditId\":\"" + auditId + "\"}");
        }
        responseWrapper.copyBodyToResponse();
    }

    private static boolean containsForbiddenBodyField(byte[] responseBody) throws IOException {
        try (JsonParser jsonParser =
                JSON_FACTORY.createParser(
                        ObjectReadContext.empty(), new ByteArrayInputStream(responseBody))) {
            JsonToken jsonToken;
            while ((jsonToken = jsonParser.nextToken()) != null) {
                if (jsonToken != JsonToken.PROPERTY_NAME) {
                    continue;
                }
                String propertyName = jsonParser.currentName();
                JsonToken valueToken = jsonParser.nextToken();
                if (AdminBodyBanRegex.FORBIDDEN_FIELD_NAME.matcher(propertyName).matches()
                        && valueToken == JsonToken.VALUE_STRING
                        && jsonParser.getString().length() > MAX_ALLOWED_FORBIDDEN_STRING_LENGTH) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean isJson(String contentType) {
        return contentType != null
                && MediaType.parseMediaType(contentType)
                        .isCompatibleWith(MediaType.APPLICATION_JSON);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
