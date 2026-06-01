package com.zeromail.core.admin.mkey.persistence;

import com.zeromail.core.llm.domain.LlmProvider;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class LlmProviderAttributeConverter implements AttributeConverter<LlmProvider, String> {

    @Override
    public String convertToDatabaseColumn(LlmProvider provider) {
        return provider == null ? null : provider.id();
    }

    @Override
    public LlmProvider convertToEntityAttribute(String databaseColumn) {
        return databaseColumn == null ? null : LlmProvider.fromId(databaseColumn);
    }
}
