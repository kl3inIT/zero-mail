package com.zeromail.core.admin.audit.usecases;

import com.zeromail.core.admin.audit.persistence.lowlevel.AdminAuditEventWriteRepository;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditChainVerifier {

    private final AdminAuditEventWriteRepository adminAuditEventWriteRepository;
    private final AdminAuditHmacSecretProvider adminAuditHmacSecretProvider;
    private final HmacChainHasher hmacChainHasher;

    public AdminAuditChainVerifier(
            AdminAuditEventWriteRepository adminAuditEventWriteRepository,
            AdminAuditHmacSecretProvider adminAuditHmacSecretProvider) {
        this.adminAuditEventWriteRepository =
                Objects.requireNonNull(
                        adminAuditEventWriteRepository,
                        "adminAuditEventWriteRepository must not be null");
        this.adminAuditHmacSecretProvider =
                Objects.requireNonNull(
                        adminAuditHmacSecretProvider,
                        "adminAuditHmacSecretProvider must not be null");
        hmacChainHasher = new HmacChainHasher();
    }

    @Transactional(readOnly = true)
    public OptionalLong verifyOnce() {
        List<HmacChainHasher.AuditChainEntry> auditChainEntries =
                adminAuditEventWriteRepository.findAllEntriesInChainOrder();
        return hmacChainHasher.findFirstMismatch(
                adminAuditHmacSecretProvider.secret(), auditChainEntries);
    }
}
