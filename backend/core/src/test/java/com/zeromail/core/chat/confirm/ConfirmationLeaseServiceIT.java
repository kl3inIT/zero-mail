package com.zeromail.core.chat.confirm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.shared.lock.LockBackendUnavailableException;
import com.zeromail.core.tenant.TenantContext;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ConfirmationLeaseServiceIT {

    @Test
    void try_acquire_sets_five_minute_lease_and_reports_held_key() {
        UUID tenantId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = valueOperations(stringRedisTemplate);
        String leaseKey = "chat-confirm:" + chatId + ":tool-send-1";
        when(valueOperations.setIfAbsent(leaseKey, "process-1", Duration.ofMinutes(5)))
                .thenReturn(true);
        when(valueOperations.setIfAbsent(leaseKey, "process-2", Duration.ofMinutes(5)))
                .thenReturn(false);
        ConfirmationLeaseService confirmationLeaseService =
                new ConfirmationLeaseService(() -> stringRedisTemplate);

        boolean acquired =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        confirmationLeaseService.tryAcquire(
                                                chatId, "tool-send-1", "process-1"));
        boolean reacquired =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        confirmationLeaseService.tryAcquire(
                                                chatId, "tool-send-1", "process-2"));

        assertThat(acquired).isTrue();
        assertThat(reacquired).isFalse();
    }

    @Test
    void release_deletes_confirmation_lease_key() {
        UUID chatId = UUID.randomUUID();
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ConfirmationLeaseService confirmationLeaseService =
                new ConfirmationLeaseService(() -> stringRedisTemplate);

        confirmationLeaseService.release(chatId, "tool-send-1");

        verify(stringRedisTemplate).delete("chat-confirm:" + chatId + ":tool-send-1");
    }

    @Test
    void try_acquire_throws_when_redis_is_unavailable() {
        ConfirmationLeaseService confirmationLeaseService =
                new ConfirmationLeaseService(() -> null);

        assertThatThrownBy(
                        () ->
                                confirmationLeaseService.tryAcquire(
                                        UUID.randomUUID(), "tool-send-1", "process-1"))
                .isInstanceOf(LockBackendUnavailableException.class);
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> valueOperations(
            StringRedisTemplate stringRedisTemplate) {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        return valueOperations;
    }
}
