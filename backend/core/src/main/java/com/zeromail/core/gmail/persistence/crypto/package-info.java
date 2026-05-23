/**
 * Refresh-token AES-GCM envelope cipher and its Spring configuration. Exposed as a narrow named
 * interface so the LLM module can reuse the cipher for BYOK key encryption without opening the rest
 * of Gmail persistence.
 */
@org.springframework.modulith.NamedInterface("persistence.crypto")
package com.zeromail.core.gmail.persistence.crypto;
