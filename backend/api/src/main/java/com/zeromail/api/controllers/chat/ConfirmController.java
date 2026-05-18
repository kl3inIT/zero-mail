package com.zeromail.api.controllers.chat;

import com.zeromail.api.dto.chat.ConfirmActionRequestDto;
import com.zeromail.api.dto.chat.ConfirmActionResponseDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "Chat")
@RequestMapping("/api/chat")
public class ConfirmController {

    @PostMapping("/{chatId}/confirm")
    public ConfirmActionResponseDto confirm(
            @PathVariable UUID chatId, @Valid @RequestBody ConfirmActionRequestDto request) {
        Objects.requireNonNull(chatId, "chatId");
        Objects.requireNonNull(request, "request");
        // TODO: Wave 4 - wire AssistantSendExecutor + ConfirmationLeaseService + state machine
        // [ATOMIC-GROUP: arch01-flip]
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED, "Confirm endpoint ships in Wave 4");
    }
}
