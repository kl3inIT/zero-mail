package com.zeromail.api.security;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * Resolves the per-browser-session active Gmail mailbox for a tenant.
 *
 * <p>The session attribute is intentionally tenant-namespaced as {@code
 * active_gmail_mailbox_id::{tenantId}}. Cross-device stickiness is intentionally not delivered in
 * v1.3 because Spring Session state is scoped to the browser session; a new device falls back to
 * the tenant's primary connected mailbox until the user switches there too.
 *
 * <p>Stored values are not trusted blindly. Every request re-validates ownership and CONNECTED
 * state. No connected mailbox returns {@code null} so the filter can pass through unbound;
 * disconnected or foreign stored values fall back to primary and overwrite or clear the stale
 * attribute; disconnecting the currently active mailbox is handled on the next request by the same
 * revalidation path.
 */
@Component
public class ActiveMailboxResolver {

    static final String ACTIVE_MAILBOX_SESSION_ATTRIBUTE_PREFIX = "active_gmail_mailbox_id::";

    private final GmailConnectionRepository connectionRepository;
    private final GmailConnectionService gmailConnectionService;

    public ActiveMailboxResolver(
            GmailConnectionRepository connectionRepository,
            GmailConnectionService gmailConnectionService) {
        this.connectionRepository =
                Objects.requireNonNull(
                        connectionRepository, "connectionRepository must not be null");
        this.gmailConnectionService =
                Objects.requireNonNull(
                        gmailConnectionService, "gmailConnectionService must not be null");
    }

    public UUID resolveOrPrimary(HttpServletRequest request, UUID tenantId) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        Optional<UUID> storedMailboxId = storedActiveMailboxId(request, tenantId);
        if (storedMailboxId.isPresent()) {
            Optional<GmailConnectionEntity> storedConnection =
                    connectionRepository.findByIdAndTenantId(
                            storedMailboxId.orElseThrow(), tenantId);
            if (storedConnection
                    .filter(
                            gmailConnection ->
                                    gmailConnection.getStatus() == GmailConnectionStatus.CONNECTED)
                    .isPresent()) {
                return storedMailboxId.orElseThrow();
            }
        }

        return fallbackToPrimary(request, tenantId).map(GmailConnectionEntity::getId).orElse(null);
    }

    public Optional<GmailConnectionEntity> resolveConnectionOrPrimary(
            HttpServletRequest request, UUID tenantId) {
        UUID gmailConnectionId = resolveOrPrimary(request, tenantId);
        if (gmailConnectionId == null) {
            return Optional.empty();
        }
        return connectionRepository
                .findByIdAndTenantId(gmailConnectionId, tenantId)
                .filter(
                        gmailConnection ->
                                gmailConnection.getStatus() == GmailConnectionStatus.CONNECTED);
    }

    public GmailConnectionEntity setActive(
            HttpServletRequest request, UUID tenantId, UUID gmailConnectionId) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailConnectionId, "gmailConnectionId must not be null");
        GmailConnectionEntity gmailConnection =
                gmailConnectionService.resolveOwnedConnectionOrThrow(tenantId, gmailConnectionId);
        request.getSession(true)
                .setAttribute(activeMailboxSessionAttribute(tenantId), gmailConnection.getId());
        return gmailConnection;
    }

    private Optional<UUID> storedActiveMailboxId(HttpServletRequest request, UUID tenantId) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        String attributeName = activeMailboxSessionAttribute(tenantId);
        Object activeMailboxValue = session.getAttribute(attributeName);
        if (activeMailboxValue instanceof UUID gmailConnectionId) {
            return Optional.of(gmailConnectionId);
        }
        if (activeMailboxValue instanceof String gmailConnectionIdText
                && !gmailConnectionIdText.isBlank()) {
            try {
                return Optional.of(UUID.fromString(gmailConnectionIdText));
            } catch (IllegalArgumentException invalidStoredMailboxId) {
                session.removeAttribute(attributeName);
                return Optional.empty();
            }
        }
        if (activeMailboxValue != null) {
            session.removeAttribute(attributeName);
        }
        return Optional.empty();
    }

    private Optional<GmailConnectionEntity> fallbackToPrimary(
            HttpServletRequest request, UUID tenantId) {
        Optional<GmailConnectionEntity> primaryConnection =
                connectionRepository
                        .findByTenantId(tenantId)
                        .filter(
                                gmailConnection ->
                                        gmailConnection.getStatus()
                                                == GmailConnectionStatus.CONNECTED);
        Optional<GmailConnectionEntity> connectedFallback =
                primaryConnection.isPresent()
                        ? primaryConnection
                        : connectionRepository
                                .findPrimaryMailboxCandidatesByTenantId(tenantId, Limit.of(10))
                                .stream()
                                .filter(
                                        gmailConnection ->
                                                gmailConnection.getStatus()
                                                        == GmailConnectionStatus.CONNECTED)
                                .findFirst();

        HttpSession session = request.getSession(false);
        if (session == null) {
            return connectedFallback;
        }
        String attributeName = activeMailboxSessionAttribute(tenantId);
        if (connectedFallback.isPresent()) {
            session.setAttribute(attributeName, connectedFallback.orElseThrow().getId());
        } else {
            session.removeAttribute(attributeName);
        }
        return connectedFallback;
    }

    static String activeMailboxSessionAttribute(UUID tenantId) {
        return ACTIVE_MAILBOX_SESSION_ATTRIBUTE_PREFIX + tenantId;
    }
}
