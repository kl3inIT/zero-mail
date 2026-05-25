package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.SuppressionReason;
import com.zeromail.core.cleanup.persistence.SenderSuppressionEntity;
import com.zeromail.core.cleanup.persistence.SenderSuppressionRepository;
import com.zeromail.core.cleanup.projection.SenderSuppressionProjection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD service for the per-tenant sender suppression list (UNS-02).
 *
 * <p>Each row suppresses EITHER a specific {@code sender_email} OR a {@code sender_domain}, never
 * both — XOR enforced at three layers (most-specific first): application-level pre-validation in
 * this service ({@link AddSuppressionCommand}), entity constructor invariant in {@link
 * SenderSuppressionEntity}, and the {@code ck_sender_suppression_one_target} DB CHECK constraint
 * from changelog 043.
 *
 * <p>Privacy invariant: this service never logs raw {@code senderEmail} values. The log line
 * reports only the target shape ({@code "email"} vs {@code "domain"}) plus the suppression reason —
 * enough for triage without leaking sender PII.
 */
@Service
@Transactional
public class SuppressionCrudService {

    private static final Logger log = LoggerFactory.getLogger(SuppressionCrudService.class);

    private final SenderSuppressionRepository senderSuppressionRepository;

    public SuppressionCrudService(SenderSuppressionRepository senderSuppressionRepository) {
        this.senderSuppressionRepository =
                Objects.requireNonNull(
                        senderSuppressionRepository,
                        "senderSuppressionRepository must not be null");
    }

    /**
     * Manually add a suppression entry for the tenant. Exactly one of {@link
     * AddSuppressionCommand#senderEmail()} / {@link AddSuppressionCommand#senderDomain()} must be
     * non-null (XOR).
     *
     * <p>Idempotent: if a row with the same {@code (tenantId, senderEmail)} or {@code (tenantId,
     * senderDomain)} already exists, the existing projection is returned and no new row is
     * persisted. Mirrors the partial-unique-index semantics from changelog 043.
     */
    public SenderSuppressionProjection addManual(UUID tenantId, AddSuppressionCommand command) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        if (command.isSenderEmail()) {
            Optional<SenderSuppressionEntity> existing =
                    senderSuppressionRepository.findByTenantIdAndSenderEmail(
                            tenantId, command.senderEmail());
            if (existing.isPresent()) {
                logSuppressionAdded(tenantId, command, "email", "idempotent");
                return SenderSuppressionProjection.from(existing.orElseThrow());
            }
        } else {
            Optional<SenderSuppressionEntity> existing =
                    senderSuppressionRepository.findByTenantIdAndSenderDomain(
                            tenantId, command.senderDomain());
            if (existing.isPresent()) {
                logSuppressionAdded(tenantId, command, "domain", "idempotent");
                return SenderSuppressionProjection.from(existing.orElseThrow());
            }
        }

        SenderSuppressionEntity entity =
                new SenderSuppressionEntity(
                        UUID.randomUUID(),
                        tenantId,
                        command.senderEmail(),
                        command.senderDomain(),
                        command.reason());
        SenderSuppressionEntity persisted = senderSuppressionRepository.save(entity);
        logSuppressionAdded(
                tenantId, command, command.isSenderEmail() ? "email" : "domain", "created");
        return SenderSuppressionProjection.from(persisted);
    }

    /**
     * List all suppression entries for the tenant, newest first. Returned projections are defensive
     * copies — callers cannot mutate the underlying entity.
     */
    @Transactional(readOnly = true)
    public List<SenderSuppressionProjection> list(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        List<SenderSuppressionEntity> entities =
                senderSuppressionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return entities.stream().map(SenderSuppressionProjection::from).toList();
    }

    /**
     * Fetch a single suppression entry by id, tenant-scoped (defense-in-depth: even if the caller
     * passes a guessed UUID from another tenant, the repository filter returns empty).
     */
    @Transactional(readOnly = true)
    public Optional<SenderSuppressionProjection> findById(UUID tenantId, UUID suppressionId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(suppressionId, "suppressionId must not be null");
        return senderSuppressionRepository
                .findByIdAndTenantId(suppressionId, tenantId)
                .map(SenderSuppressionProjection::from);
    }

    /**
     * Remove a suppression entry by id, tenant-scoped. Throws {@link NoSuchElementException} if the
     * row does not exist (controller maps to 404).
     */
    public void remove(UUID tenantId, UUID suppressionId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(suppressionId, "suppressionId must not be null");
        SenderSuppressionEntity entity =
                senderSuppressionRepository
                        .findByIdAndTenantId(suppressionId, tenantId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "sender_suppression row not found for tenant"));
        senderSuppressionRepository.delete(entity);
        log.info(
                "event=cleanup_suppression_removed tenantId={} suppressionId={}",
                tenantId,
                suppressionId);
    }

    private void logSuppressionAdded(
            UUID tenantId, AddSuppressionCommand command, String targetShape, String outcome) {
        log.info(
                "event=cleanup_suppression_added tenantId={} reason={} target={} outcome={}",
                tenantId,
                command.reason().id(),
                targetShape,
                outcome);
    }

    /**
     * Command for {@link #addManual(UUID, AddSuppressionCommand)}. Exactly one of {@code
     * senderEmail} / {@code senderDomain} must be non-null and non-blank — enforced in the compact
     * constructor so the violation never reaches the entity layer.
     */
    public record AddSuppressionCommand(
            String senderEmail, String senderDomain, SuppressionReason reason) {

        public AddSuppressionCommand {
            Objects.requireNonNull(reason, "reason must not be null");
            boolean emailPresent = senderEmail != null && !senderEmail.isBlank();
            boolean domainPresent = senderDomain != null && !senderDomain.isBlank();
            if (emailPresent == domainPresent) {
                throw new IllegalArgumentException(
                        "exactly one of senderEmail or senderDomain must be non-null (XOR)");
            }
            // Normalize to null for the blank side so the entity's XOR check has a clean null.
            if (!emailPresent) {
                senderEmail = null;
            }
            if (!domainPresent) {
                senderDomain = null;
            }
        }

        public boolean isSenderEmail() {
            return senderEmail != null;
        }
    }
}
