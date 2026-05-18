package com.zeromail.core.chat.confirm;

import com.zeromail.core.shared.lock.LockBackendUnavailableException;
import com.zeromail.core.tenant.TenantContext;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConfirmationLeaseService {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationLeaseService.class);
    private static final Duration CONFIRMATION_LEASE_TTL = Duration.ofMinutes(5);

    private final Supplier<StringRedisTemplate> stringRedisTemplateSupplier;

    @Autowired
    public ConfirmationLeaseService(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this(stringRedisTemplateProvider::getIfAvailable);
    }

    public ConfirmationLeaseService(Supplier<StringRedisTemplate> stringRedisTemplateSupplier) {
        this.stringRedisTemplateSupplier =
                Objects.requireNonNull(
                        stringRedisTemplateSupplier,
                        "stringRedisTemplateSupplier must not be null");
    }

    public boolean tryAcquire(UUID chatId, String toolCallId, String processInstanceId) {
        String lockKey = lockKey(chatId, toolCallId);
        String leaseValue = requireText(processInstanceId, "processInstanceId");
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateSupplier.get();
        if (stringRedisTemplate == null) {
            log.warn("event=chat_confirmation_lease_unavailable tenantId={}", tenantIdForLog());
            throw new LockBackendUnavailableException();
        }
        Boolean acquired;
        try {
            acquired =
                    stringRedisTemplate
                            .opsForValue()
                            .setIfAbsent(lockKey, leaseValue, CONFIRMATION_LEASE_TTL);
        } catch (RuntimeException redisFailure) {
            log.warn(
                    "event=chat_confirmation_lease_unavailable tenantId={} reason={}",
                    tenantIdForLog(),
                    redisFailure.getClass().getSimpleName());
            throw new LockBackendUnavailableException(redisFailure);
        }
        if (Boolean.TRUE.equals(acquired)) {
            log.info(
                    "event=chat_confirmation_lease_acquired tenantId={} chatId={} ttlSeconds={}",
                    tenantIdForLog(),
                    chatId,
                    CONFIRMATION_LEASE_TTL.toSeconds());
            return true;
        }
        return false;
    }

    public void release(UUID chatId, String toolCallId) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateSupplier.get();
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(lockKey(chatId, toolCallId));
            log.info(
                    "event=chat_confirmation_lease_released tenantId={} chatId={}",
                    tenantIdForLog(),
                    chatId);
        } catch (RuntimeException redisFailure) {
            log.debug(
                    "event=chat_confirmation_lease_release_failed tenantId={} reason={}",
                    tenantIdForLog(),
                    redisFailure.getClass().getSimpleName());
        }
    }

    private static String lockKey(UUID chatId, String toolCallId) {
        Objects.requireNonNull(chatId, "chatId must not be null");
        return "chat-confirm:" + chatId + ":" + requireText(toolCallId, "toolCallId");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    private static String tenantIdForLog() {
        try {
            return TenantContext.currentOrThrow();
        } catch (RuntimeException tenantContextMissing) {
            return "unknown";
        }
    }
}
