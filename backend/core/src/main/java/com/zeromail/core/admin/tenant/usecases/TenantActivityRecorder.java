package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.tenant.persistence.lowlevel.TenantActivityEventWriteRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantActivityRecorder {

    private static final String AUTH_SOURCE = "AUTH";
    private static final String SUCCESS_STATUS = "SUCCESS";

    private final TenantActivityEventWriteRepository tenantActivityEventWriteRepository;

    public TenantActivityRecorder(
            TenantActivityEventWriteRepository tenantActivityEventWriteRepository) {
        this.tenantActivityEventWriteRepository =
                Objects.requireNonNull(
                        tenantActivityEventWriteRepository,
                        "tenantActivityEventWriteRepository must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(
            UUID tenantId, TenantActivityRequestContext requestContext, Instant occurredAt) {
        tenantActivityEventWriteRepository.insert(
                tenantId,
                "LOGIN",
                SUCCESS_STATUS,
                "Đăng nhập vào ứng dụng",
                requestContext,
                occurredAt,
                null,
                AUTH_SOURCE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogout(
            UUID tenantId,
            TenantActivityRequestContext requestContext,
            Instant occurredAt,
            int durationSeconds) {
        tenantActivityEventWriteRepository.insert(
                tenantId,
                "LOGOUT",
                SUCCESS_STATUS,
                "Đăng xuất khỏi ứng dụng",
                requestContext,
                occurredAt,
                durationSeconds,
                AUTH_SOURCE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSessionExpired(
            UUID tenantId,
            TenantActivityRequestContext requestContext,
            Instant occurredAt,
            int durationSeconds) {
        tenantActivityEventWriteRepository.insert(
                tenantId,
                "SESSION_EXPIRED",
                SUCCESS_STATUS,
                "Phiên đăng nhập hết hạn",
                requestContext,
                occurredAt,
                durationSeconds,
                AUTH_SOURCE);
    }
}
