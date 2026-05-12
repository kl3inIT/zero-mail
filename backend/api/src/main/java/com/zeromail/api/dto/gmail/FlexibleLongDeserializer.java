package com.zeromail.api.dto.gmail;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class FlexibleLongDeserializer extends StdDeserializer<Long> {

    public FlexibleLongDeserializer() {
        super(Long.class);
    }

    @Override
    public Long deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws JacksonException {
        if (jsonParser.currentToken() == JsonToken.VALUE_STRING) {
            return Long.parseLong(jsonParser.getString().trim());
        }
        return jsonParser.getLongValue();
    }
}
