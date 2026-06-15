package com.zeromail.core.messaging.telegram.usecases;

import com.zeromail.core.messaging.telegram.persistence.TelegramAccountJdbcRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisconnectTelegramService {

    private final TelegramAccountJdbcRepository telegramAccountJdbcRepository;
    private final Clock clock;

    public DisconnectTelegramService(
            TelegramAccountJdbcRepository telegramAccountJdbcRepository, Clock clock) {
        this.telegramAccountJdbcRepository = telegramAccountJdbcRepository;
        this.clock = clock;
    }

    @Transactional
    public void disconnect(UUID tenantId) {
        telegramAccountJdbcRepository.markDisconnected(tenantId, clock.instant());
    }
}
