package com.zeromail.core.shared.lang;

/**
 * Base enum contract: every domain enum exposes a stable {@link #id()} string used for persistence
 * + i18n, and an i18n {@link #labelKey()} derived from that id.
 *
 * <p><b>Implements decisions D-B1 (two-interface design), D-B2 (id type = String), D-B3 (labelKey
 * default format), D-C2 (id() == name() invariant).</b>
 *
 * <p><b>Invariant (D-C2):</b> when an implementer is an {@link Enum}, the {@link #id()} MUST equal
 * {@link Enum#name()}. This lets {@code @Enumerated(EnumType.STRING)} continue to persist the same
 * string value as {@code id()} reads. Storage-mechanism upgrade ({@code AttributeConverter}) is
 * deferred until the first enum needs to decouple Java symbol from DB value (D-C3 trigger).
 * Enforcement of this invariant is via:
 *
 * <ol>
 *   <li>Documented convention (here + {@code package-info.java})
 *   <li>{@code @Enumerated(EnumType.STRING)} on the entity field
 *   <li>{@code OnboardingStepPersistenceTest} (Plan 03) round-trip + raw column assert
 * </ol>
 *
 * <p><b>fromId pattern (D-B4):</b> idiomatic Java per-impl static method. Static methods cannot be
 * in interfaces as abstract, so each enum carries its own:
 *
 * <pre>
 *   public static MyEnum fromId(String id) {
 *       return Stream.of(values())
 *               .filter(e -&gt; e.id().equals(id))
 *               .findFirst()
 *               .orElseThrow(() -&gt; new NoSuchElementException("Unknown MyEnum id: " + id));
 *   }
 * </pre>
 *
 * Throws {@link java.util.NoSuchElementException} (NOT {@link IllegalArgumentException}) —
 * fail-loud preference per D-B4.
 *
 * <p><b>Design rationale:</b> adapted from Jmix {@code EnumClass<T>} interface (we drop the
 * framework dep). JHipster has no enum-side equivalent — we lifted Jmix's contract shape but not
 * its generic ID type (D-B2: String only for current and near-term scope; if an Integer ID emerges
 * later, introduce a parallel {@code IdentifiedIntEnum} rather than retrofitting generics).
 */
public interface IdentifiedEnum {

    /**
     * Stable string id used for persistence + i18n. MUST equal {@link Enum#name()} when the
     * implementer is an enum (D-C2 invariant).
     */
    String id();

    /**
     * Default i18n bundle key: {@code <ClassSimpleName>.<id>} (e.g. {@code
     * OnboardingStep.GMAIL_CONNECTED}). Override only when the default is wrong for some reason
     * (D-B3).
     */
    default String labelKey() {
        return getClass().getSimpleName() + "." + id();
    }
}
