package com.zeromail.core.shared.privacy;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@Component
public class SensitiveJacksonModule extends SimpleModule {

  public SensitiveJacksonModule() {
    @SuppressWarnings("unchecked")
    Class<Sensitive<?>> sensitiveType = (Class<Sensitive<?>>) (Class<?>) Sensitive.class;
    addSerializer(
        sensitiveType,
        new ValueSerializer<>() {
          @Override
          public void serialize(
              Sensitive<?> sensitiveValue,
              JsonGenerator jsonGenerator,
              SerializationContext serializationContext)
              throws JacksonException {
            jsonGenerator.writeString("***REDACTED***");
          }
        });
  }
}
