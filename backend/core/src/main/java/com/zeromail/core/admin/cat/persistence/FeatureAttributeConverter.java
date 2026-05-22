package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class FeatureAttributeConverter implements AttributeConverter<Feature, String> {

    @Override
    public String convertToDatabaseColumn(Feature feature) {
        return feature == null ? null : feature.id();
    }

    @Override
    public Feature convertToEntityAttribute(String databaseColumn) {
        return databaseColumn == null ? null : Feature.fromId(databaseColumn);
    }
}
