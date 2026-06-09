package com.zeromail.core.messaging.telegram.usecases;

import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import com.zeromail.core.messaging.telegram.domain.TelegramAccountView;
import com.zeromail.core.messaging.telegram.persistence.TelegramAccountJdbcRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TelegramStatusQueryService {

    private final TelegramProperties telegramProperties;
    private final TelegramAccountJdbcRepository telegramAccountJdbcRepository;

    public TelegramStatusQueryService(
            TelegramProperties telegramProperties,
            TelegramAccountJdbcRepository telegramAccountJdbcRepository) {
        this.telegramProperties = telegramProperties;
        this.telegramAccountJdbcRepository = telegramAccountJdbcRepository;
    }

    public TelegramStatus status(UUID tenantId) {
        Optional<TelegramAccountView> account =
                telegramAccountJdbcRepository.findByTenantId(tenantId);
        return new TelegramStatus(telegramProperties.configured(), account.orElse(null));
    }

    public record TelegramStatus(boolean configured, TelegramAccountView account) {
        public boolean connected() {
            return account != null && account.connected();
        }
    }
}
