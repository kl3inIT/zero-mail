package com.zeromail.api.controllers.chat;

import com.zeromail.api.dto.chat.ConfirmActionRequestDto;
import com.zeromail.api.dto.chat.ConfirmActionResponseDto;
import com.zeromail.core.chat.exception.ConfirmationLeaseConflictException;
import com.zeromail.core.chat.exception.GmailSendFailedException;
import com.zeromail.core.chat.exception.PendingActionNotFoundException;
import com.zeromail.core.chat.exception.StaleToolCallException;
import com.zeromail.core.chat.exception.VipAcknowledgmentMissingException;
import com.zeromail.core.chat.usecases.ConfirmActionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Chat")
@RequestMapping("/api/chat")
public class ConfirmController {

    private final ConfirmActionService confirmActionService;

    public ConfirmController(ConfirmActionService confirmActionService) {
        this.confirmActionService = confirmActionService;
    }

    @PostMapping("/{chatId}/confirm")
    public ConfirmActionResponseDto confirm(
            @PathVariable UUID chatId, @Valid @RequestBody ConfirmActionRequestDto request) {
        Objects.requireNonNull(chatId, "chatId");
        Objects.requireNonNull(request, "request");
        return response(
                confirmActionService.confirm(
                        chatId,
                        request.toolCallId(),
                        request.vipAcknowledged(),
                        request.contentOverride()));
    }

    @PostMapping("/{chatId}/cancel")
    public ConfirmActionResponseDto cancel(
            @PathVariable UUID chatId, @Valid @RequestBody ConfirmActionRequestDto request) {
        Objects.requireNonNull(chatId, "chatId");
        Objects.requireNonNull(request, "request");
        return response(confirmActionService.cancel(chatId, request.toolCallId()));
    }

    private static ConfirmActionResponseDto response(
            ConfirmActionService.ConfirmActionResult result) {
        return new ConfirmActionResponseDto(result.state());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({
        PendingActionNotFoundException.class,
        StaleToolCallException.class
    })
    ResponseEntity<Void> pendingActionNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({
        ConfirmationLeaseConflictException.class,
        VipAcknowledgmentMissingException.class
    })
    ResponseEntity<Void> confirmationConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(GmailSendFailedException.class)
    ResponseEntity<Void> gmailSendFailed() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }
}
