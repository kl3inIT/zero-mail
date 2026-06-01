/**
 * BYOK endpoint validation, credential resolution, and rate limiting for tenant-supplied LLM
 * provider keys. Consumed by the chat module's Spring-AI model factory and voice generation.
 */
@org.springframework.modulith.NamedInterface("byok")
package com.zeromail.core.llm.byok;
