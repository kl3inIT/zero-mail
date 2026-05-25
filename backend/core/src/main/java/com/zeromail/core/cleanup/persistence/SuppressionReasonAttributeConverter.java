package com.zeromail.core.cleanup.persistence;

import com.zeromail.core.cleanup.domain.SuppressionReason;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link SuppressionReason} enum constants to the lowercase string values mandated by the DB
 * CHECK constraint on {@code sender_suppression.reason} (changelog 043: {@code reason IN
 * ('manual','replied','auto')}).
 *
 * <p>This is the D-C3 trigger documented on {@link com.zeromail.core.shared.lang.IdentifiedEnum}:
 * when the Java symbol ({@code MANUAL}) cannot equal the DB value ({@code "manual"}), persistence
 * goes through an {@link AttributeConverter} so {@code @Enumerated(EnumType.STRING)} is no longer
 * sufficient.
 *
 * <p><b>{@code autoApply = true}:</b> every {@code SuppressionReason} field on any entity in any
 * module is routed through this converter automatically; entities do not need to repeat
 * {@code @Convert(converter = ...)} on the field. Plan 08-03 ships exactly one such field ({@link
 * SenderSuppressionEntity#reason}); any future entity carrying a SuppressionReason picks this up
 * implicitly.
 */
@Converter(autoApply = true)
public class SuppressionReasonAttributeConverter
        implements AttributeConverter<SuppressionReason, String> {

    @Override
    public String convertToDatabaseColumn(SuppressionReason reason) {
        return reason == null ? null : reason.id();
    }

    @Override
    public SuppressionReason convertToEntityAttribute(String databaseColumn) {
        return databaseColumn == null ? null : SuppressionReason.fromId(databaseColumn);
    }
}
