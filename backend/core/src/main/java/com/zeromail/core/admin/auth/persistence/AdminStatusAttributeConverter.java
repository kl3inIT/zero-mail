package com.zeromail.core.admin.auth.persistence;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
class AdminStatusAttributeConverter implements AttributeConverter<AdminStatus, String> {

    @Override
    public String convertToDatabaseColumn(AdminStatus attribute) {
        return attribute == null ? null : attribute.id();
    }

    @Override
    public AdminStatus convertToEntityAttribute(String databaseValue) {
        return databaseValue == null ? null : AdminStatus.fromId(databaseValue);
    }
}
