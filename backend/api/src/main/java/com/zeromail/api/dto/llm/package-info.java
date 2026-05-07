/**
 * LLM-domain HTTP wire DTOs for BYOK validation, saving, and current credential metadata.
 *
 * <p>Exposed as part of the {@code dto} application module's public API so LLM controllers can
 * reach these records across Spring Modulith module boundaries.
 */
@org.springframework.modulith.NamedInterface("llm")
package com.zeromail.api.dto.llm;
