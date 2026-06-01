/**
 * JPA + native UPSERT for the Gmail inbox projection tables.
 *
 * <p>Entities are Hibernate-managed classes (NOT records, never Lombok). Ciphertext columns are
 * stored as {@code bytea}; plaintext encrypt/decrypt happens at the use-case boundary via the
 * inbox projection cipher.
 */
@org.springframework.modulith.NamedInterface("persistence")
package com.zeromail.core.inbox.persistence;
