package com.zeromail.api.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;

public class PathRoutingCookieSerializer implements CookieSerializer {

    private final CookieSerializer adminCookieSerializer;
    private final CookieSerializer userCookieSerializer;

    public PathRoutingCookieSerializer(
            CookieSerializer adminCookieSerializer, CookieSerializer userCookieSerializer) {
        this.adminCookieSerializer = adminCookieSerializer;
        this.userCookieSerializer = userCookieSerializer;
    }

    @Override
    public List<String> readCookieValues(HttpServletRequest request) {
        return serializerFor(request).readCookieValues(request);
    }

    @Override
    public void writeCookieValue(CookieValue cookieValue) {
        serializerFor(cookieValue.getRequest()).writeCookieValue(cookieValue);
    }

    private CookieSerializer serializerFor(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/api/admin/")
                || requestUri.startsWith("/webauthn/")
                || requestUri.startsWith("/login/webauthn")) {
            return adminCookieSerializer;
        }
        return userCookieSerializer;
    }
}
