package com.zeromail.api.controllers.integrations;

import com.zeromail.core.messaging.telegram.webhook.TelegramUpdateRequest;
import com.zeromail.core.messaging.telegram.webhook.TelegramUpdateRouter;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class TelegramWebhookController {

    private final TelegramUpdateRouter telegramUpdateRouter;

    public TelegramWebhookController(TelegramUpdateRouter telegramUpdateRouter) {
        this.telegramUpdateRouter = telegramUpdateRouter;
    }

    @PostMapping("/api/integrations/telegram/webhook")
    public void receive(@RequestBody TelegramUpdateRequest updateRequest) {
        telegramUpdateRouter.route(updateRequest);
    }
}
