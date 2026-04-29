package com.zeromail.api.controllers;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.gmail.GmailNotification;
import com.zeromail.api.dto.gmail.PubSubPushEnvelope;
import com.zeromail.core.gmail.service.PubSubIngestionService;

import io.swagger.v3.oas.annotations.Hidden;
import tools.jackson.databind.ObjectMapper;

@Hidden
@RestController
public class GmailPubSubController {

    private static final Logger log = LoggerFactory.getLogger(GmailPubSubController.class);

    private final PubSubIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public GmailPubSubController(PubSubIngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/internal/pubsub/gmail")
    public void receivePush(@RequestBody PubSubPushEnvelope envelope) {
        if (envelope.message() == null || envelope.message().data() == null) {
            return;
        }

        GmailNotification notification;
        try {
            byte[] decoded = Base64.getDecoder().decode(envelope.message().data());
            notification = objectMapper.readValue(decoded, GmailNotification.class);
        } catch (Exception e) {
            log.warn("event=pubsub_payload_decode_failed");
            return;
        }

        if (notification.emailAddress() == null || notification.historyId() == null) {
            log.warn("event=pubsub_payload_missing_fields");
            return;
        }
        if (envelope.message().messageId() == null || envelope.message().messageId().isBlank()) {
            log.warn("event=pubsub_missing_message_id");
            return;
        }

        ingestionService.ingestPushEnvelope(
                notification.emailAddress(),
                envelope.message().messageId(),
                notification.historyId(),
                "{}");
    }
}
