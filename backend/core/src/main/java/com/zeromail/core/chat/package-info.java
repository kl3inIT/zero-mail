/**
 * Chat assistant domain: streaming conversations, tool-call interception, confirmation state, chat
 * persistence, and assistant personalization.
 *
 * <p><b>Privacy invariant.</b> Chat may persist user-typed configuration input and structured tool
 * I/O under the project privacy carve-out, but it MUST NOT log raw email-content prompts,
 * completions, extracted Gmail bodies, recipient addresses, subjects, or tool arguments containing
 * body content. Email-read tool output body fields are stripped before persistence.
 *
 * <p><b>Sub-packages:</b>
 *
 * <ul>
 *   <li>{@code domain} - framework-free chat vocabulary, enums, and tool argument records
 *   <li>{@code usecases} - Spring-AI-free orchestration contracts and tool catalog
 *   <li>{@code persistence} - chat JPA/JDBC entities, repositories, and JSONB converters
 *   <li>{@code sanitize} - body-content and personalization sanitizers
 *   <li>{@code llm} - streaming helpers; Spring AI imports are confined to {@code llm.springai}
 * </ul>
 */
@ApplicationModule(
        displayName = "Chat (assistant streaming + confirmation)",
        allowedDependencies = {
            "llm",
            "llm :: domain",
            "llm :: usecases",
            "admin",
            "admin :: cat.events",
            "admin :: cat.usecases",
            "admin :: mkey.domain",
            "admin :: mkey.events",
            "admin :: mkey.usecases",
            "rules",
            "rules :: domain",
            "rules :: projection",
            "rules :: usecases",
            "gmail",
            "gmail :: gateway",
            "triage",
            "triage :: domain",
            "triage :: usecases",
            "tenant",
            "config",
            "shared :: lock",
            "shared :: persistence",
            "shared :: lang",
            "shared :: privacy"
        })
package com.zeromail.core.chat;

import org.springframework.modulith.ApplicationModule;
