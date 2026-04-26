/**
 * Cross-cutting enum standard: {@link IdentifiedEnum} (id + default labelKey) and
 * {@link OrderedEnum} (+ weight). The contract that domain enums implement so persistence
 * is decoupled from Java symbol layout (D-C2) and ordering is decoupled from declaration
 * order (D-B5).
 *
 * <p><b>Two-interface design (D-B1):</b>
 * <ul>
 *   <li>{@link IdentifiedEnum} — every domain enum implements this. Provides stable
 *       {@code id()} (string) + default {@code labelKey()}.</li>
 *   <li>{@link OrderedEnum} extends {@link IdentifiedEnum}, adds {@code weight()}. Use only
 *       when the enum has a forward-only flow ({@code OnboardingStep}) or sortable priority.
 *       Status enums ({@code GmailConnectionStatus}) implement {@link IdentifiedEnum} only.</li>
 * </ul>
 * Rejected alternatives: a single all-in-one interface with sentinel weight (forces every
 * author to consider weight) and a generic {@code <T>} id type (no Integer use case in
 * current or near-term scope).
 *
 * <p><b>Invariant: {@code id() == name()} (D-C2):</b> when the implementer is an enum, the
 * Java symbol literal MUST equal the {@code id()} value. This lets
 * {@code @Enumerated(EnumType.STRING)} continue to persist the canonical id with no
 * {@code AttributeConverter} ceremony. ArchTest enforcement is OUT of scope for Phase 1.2.1
 * (would require ArchUnit to introspect enum constructor calls — not standard); enforce via
 * documented convention + {@code @Enumerated(STRING)} + {@code OnboardingStepPersistenceTest}
 * (Plan 03 WR-01 closure).
 *
 * <p><b>AttributeConverter migration trigger (D-C3):</b> when a future enum needs to
 * decouple its Java symbol from its DB value (e.g. rename {@code ACCOUNT_CREATED} →
 * {@code JUST_REGISTERED} while keeping DB column value {@code "ACCOUNT_CREATED"} for
 * backward compatibility), upgrade THAT enum to a per-class {@code AttributeConverter}
 * (10–15 LOC). Do not preemptively convert. Document the trigger in the enum's class
 * Javadoc when the upgrade happens.
 *
 * <p><b>labelKey() default format (D-B3):</b> {@code <ClassSimpleName>.<id>}, e.g.
 * {@code OnboardingStep.GMAIL_CONNECTED}. Frontend i18n bundles ({@code apps/web/messages/vi.json}
 * / {@code en.json}) use this exact key. Override the default only when an enum's bundle
 * key needs a different shape.
 *
 * <p><b>fromId() pattern (D-B4):</b> per-impl static method on each enum (static cannot be
 * abstract on interfaces). One-liner using {@code Stream.of(values())} + filter + findFirst
 * + {@code orElseThrow(NoSuchElementException::new)}. Fail-loud (NOT
 * {@link IllegalArgumentException}).
 *
 * <p><b>Spring Modulith naming form (CL-3 lock):</b> Cross-sibling modules MUST reference
 * this module as {@code "shared.lang"} in their {@code allowedDependencies} array. Plan 03
 * adds this literal to {@code account/}, {@code onboarding/}, and {@code gmail/}
 * package-info files when the enums first import {@code IdentifiedEnum} / {@code OrderedEnum}.
 *
 * <p><b>Design rationale:</b> adapted from Jmix {@code EnumClass<T>} (we drop the framework
 * dependency and the generic id type). JHipster has no enum-side equivalent.
 */
@ApplicationModule(displayName = "Lang", allowedDependencies = {})
package com.zeromail.core.shared.lang;

import org.springframework.modulith.ApplicationModule;
