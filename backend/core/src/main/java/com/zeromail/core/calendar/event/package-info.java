/**
 * Integration events published by the Calendar domain. Subscribers in {@code
 * core.calendar.usecases} (and, in later phases, other modules that allow-list {@code calendar ::
 * events}) listen via {@code @TransactionalEventListener(AFTER_COMMIT)}.
 */
@org.springframework.modulith.NamedInterface("events")
package com.zeromail.core.calendar.event;
