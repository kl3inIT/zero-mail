/**
 * Read-side projection records for the Calendar domain. CQRS-lite per CONVENTIONS §2 — these
 * records are built by use-case services from a join of multiple persistence-layer entities and
 * exposed to {@code backend/api} controllers (which then map to springdoc-emitted response DTOs).
 *
 * <p>Records here NEVER carry JPA entity references; they're DTO-shaped value objects safe to cross
 * the {@code core → api} boundary.
 */
@org.springframework.modulith.NamedInterface("projection")
package com.zeromail.core.calendar.projection;
