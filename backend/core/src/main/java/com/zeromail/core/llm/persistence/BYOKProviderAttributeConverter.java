package com.zeromail.core.llm.persistence;

import com.zeromail.core.llm.model.BYOKProvider;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps BYOKProvider enum constants to their lowercase database ids.
 */
@Converter(autoApply = false)
public class BYOKProviderAttributeConverter implements AttributeConverter<BYOKProvider, String> {

    @Override
    public String convertToDatabaseColumn(BYOKProvider provider) {
        return provider == null ? null : provider.id();
    }

    @Override
    public BYOKProvider convertToEntityAttribute(String databaseColumn) {
        return databaseColumn == null ? null : BYOKProvider.fromId(databaseColumn);
    }
}
