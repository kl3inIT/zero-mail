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
        addSerializer(Sensitive.class, new ValueSerializer<Sensitive>() {
            @Override
            public void serialize(Sensitive v, JsonGenerator g, SerializationContext s) throws JacksonException {
                g.writeString("***REDACTED***");
            }
        });
    }
}
