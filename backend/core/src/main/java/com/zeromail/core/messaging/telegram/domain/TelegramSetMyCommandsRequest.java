package com.zeromail.core.messaging.telegram.domain;

import java.util.List;

/** Payload for Telegram {@code setMyCommands}, which controls the command menu in clients. */
public record TelegramSetMyCommandsRequest(List<TelegramBotCommand> commands) {

    public static TelegramSetMyCommandsRequest zeroMailDefaults() {
        return new TelegramSetMyCommandsRequest(
                List.of(
                        new TelegramBotCommand(
                                "start",
                                "K\u1ebft n\u1ed1i Telegram v\u1edbi Zero Mail b\u1eb1ng m\u00e3 li\u00ean k\u1ebft"),
                        new TelegramBotCommand("help", "Xem c\u00e1c l\u1ec7nh c\u00f3 s\u1eb5n"),
                        new TelegramBotCommand("new", "T\u1ea1o \u0111o\u1ea1n chat m\u1edbi"),
                        new TelegramBotCommand(
                                "current", "Xem tr\u1ea1ng th\u00e1i k\u1ebft n\u1ed1i")));
    }

    public record TelegramBotCommand(String command, String description) {}
}
