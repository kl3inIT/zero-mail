package com.zeromail.core.shared.privacy;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

@Component
public class SensitiveJacksonModule extends SimpleModule {

    public SensitiveJacksonModule() {
        addSerializer(Sensitive.class, new JsonSerializer<Sensitive>() {
            @Override
            public void serialize(Sensitive v, JsonGenerator g, SerializerProvider s) throws IOException {
                g.writeString("***REDACTED***");
            }
        });
    }
}
