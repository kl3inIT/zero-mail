package com.zeromail.core.admin.mkey.persistence;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class KeyFormatAttributeConverter implements AttributeConverter<KeyFormat, String> {

    @Override
    public String convertToDatabaseColumn(KeyFormat keyFormat) {
        return keyFormat == null ? null : keyFormat.id();
    }

    @Override
    public KeyFormat convertToEntityAttribute(String databaseColumn) {
        return databaseColumn == null ? null : KeyFormat.fromId(databaseColumn);
    }
}
