/**
 * Inbox projection domain primitives: state enums (InboxState, InboxSyncStatus) and value types.
 *
 * <p>All enums implement {@link com.zeromail.core.shared.lang.IdentifiedEnum} with a static {@code
 * fromId} that throws {@link java.util.NoSuchElementException} on unknown ids (Convention 4,
 * fail-loud).
 */
@org.springframework.modulith.NamedInterface("domain")
package com.zeromail.core.inbox.domain;
