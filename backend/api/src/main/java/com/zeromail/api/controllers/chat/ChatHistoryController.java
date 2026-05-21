package com.zeromail.api.controllers.chat;

import com.zeromail.api.dto.chat.ChatHistoryDetailResponseDto;
import com.zeromail.api.dto.chat.ChatHistoryListResponseDto;
import com.zeromail.core.chat.exception.ChatNotFoundException;
import com.zeromail.core.chat.projection.ChatHistoryProjection;
import com.zeromail.core.chat.usecases.ChatHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Chat")
@RequestMapping("/api/chat")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping("/history")
    public ResponseEntity<ChatHistoryListResponseDto> list(
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "0") int pageOffset) {
        List<ChatHistoryProjection> chatHistory =
                chatHistoryService.listForCurrentTenant(pageSize, pageOffset);
        return ResponseEntity.ok(
                ChatHistoryListResponseDto.from(chatHistory, pageSize, pageOffset));
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<ChatHistoryDetailResponseDto> get(@PathVariable UUID chatId) {
        return ResponseEntity.ok(
                ChatHistoryDetailResponseDto.from(chatHistoryService.loadConversation(chatId)));
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> delete(@PathVariable UUID chatId) {
        chatHistoryService.softDelete(chatId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ChatNotFoundException.class)
    public ResponseEntity<Void> chatNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
