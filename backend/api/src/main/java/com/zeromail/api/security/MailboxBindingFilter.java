package com.zeromail.api.security;

import com.zeromail.core.mailbox.MailboxContext;
import com.zeromail.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MailboxBindingFilter extends OncePerRequestFilter {

    private final ActiveMailboxResolver activeMailboxResolver;

    public MailboxBindingFilter(ActiveMailboxResolver activeMailboxResolver) {
        this.activeMailboxResolver = activeMailboxResolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws ServletException, IOException {
        if (!TenantContext.TENANT.isBound()) {
            chain.doFilter(request, response);
            return;
        }
        UUID tenantId = TenantContext.currentTenantUuid();
        UUID activeMailboxId = activeMailboxResolver.resolveOrPrimary(request, tenantId);
        if (activeMailboxId == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            ScopedValue.where(MailboxContext.MAILBOX, activeMailboxId)
                    .run(
                            () -> {
                                try {
                                    chain.doFilter(request, response);
                                } catch (IOException | ServletException filterException) {
                                    throw new RuntimeException(filterException);
                                }
                            });
        } catch (RuntimeException runtimeException) {
            if (runtimeException.getCause() instanceof IOException ioException) throw ioException;
            if (runtimeException.getCause() instanceof ServletException servletException)
                throw servletException;
            throw runtimeException;
        }
    }
}
