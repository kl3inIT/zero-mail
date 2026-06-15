package com.zeromail.api.controllers.integrations;

import com.zeromail.api.dto.integrations.TelegramDisconnectResponse;
import com.zeromail.core.messaging.telegram.usecases.DisconnectTelegramService;
import com.zeromail.core.tenant.TenantContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/telegram")
public class TelegramDisconnectController {

    private final DisconnectTelegramService disconnectTelegramService;

    public TelegramDisconnectController(DisconnectTelegramService disconnectTelegramService) {
        this.disconnectTelegramService = disconnectTelegramService;
    }

    @DeleteMapping("/connection")
    public TelegramDisconnectResponse disconnect() {
        disconnectTelegramService.disconnect(TenantContext.currentTenantUuid());
        return new TelegramDisconnectResponse("DISCONNECTED");
    }
}
