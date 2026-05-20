package com.zeromail.core.admin.cat.usecases;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.llm.gateway.springai.admin.ModelsProbeClient;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ModelSchemaValidator {

    public static final Pattern MODEL_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._:/\\-]{1,128}$");

    private static final String NORMALIZED_MODELS_SCHEMA =
            """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["data"],
              "properties": {
                "data": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["id"],
                    "properties": {
                      "id": { "type": "string", "minLength": 1, "maxLength": 128 },
                      "display_name": { "type": ["string", "null"], "maxLength": 200 }
                    },
                    "additionalProperties": false
                  }
                }
              },
              "additionalProperties": false
            }
            """;

    private final ObjectMapper objectMapper;
    private final Schema normalizedModelsSchema;

    public ModelSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        SchemaRegistry schemaRegistry =
                SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        normalizedModelsSchema = schemaRegistry.getSchema(NORMALIZED_MODELS_SCHEMA);
        normalizedModelsSchema.initializeValidators();
    }

    public void validateModelId(String modelId) {
        if (modelId == null || !MODEL_ID_PATTERN.matcher(modelId).matches()) {
            throw new CatalogModelIdInvalidException();
        }
    }

    public void validateRawModels(
            LlmProvider provider, List<ModelsProbeClient.RawModel> rawModels) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(rawModels, "rawModels must not be null");
        for (ModelsProbeClient.RawModel rawModel : rawModels) {
            validateModelId(rawModel.modelId());
        }
        validateNormalizedEnvelope(rawModels);
    }

    private void validateNormalizedEnvelope(List<ModelsProbeClient.RawModel> rawModels) {
        try {
            String normalizedEnvelope =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "data",
                                    rawModels.stream()
                                            .map(
                                                    rawModel ->
                                                            Map.of(
                                                                    "id",
                                                                    rawModel.modelId(),
                                                                    "display_name",
                                                                    rawModel.displayName()))
                                            .toList()));
            var errors = normalizedModelsSchema.validate(normalizedEnvelope, InputFormat.JSON);
            if (!errors.isEmpty()) {
                throw new CatalogModelSchemaMismatchException();
            }
        } catch (JacksonException jacksonException) {
            throw new CatalogModelSchemaMismatchException();
        }
    }

    public static class CatalogModelIdInvalidException extends AdminBusinessException {
        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.catalog_model_id_invalid";
        }

        @Override
        public String logEvent() {
            return "admin_catalog_model_id_invalid";
        }

        @Override
        public String detail() {
            return "The supplied catalog model identifier is not in the accepted format.";
        }
    }

    public static class CatalogModelSchemaMismatchException extends AdminBusinessException {
        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.catalog_model_schema_mismatch";
        }

        @Override
        public String logEvent() {
            return "admin_catalog_model_schema_mismatch";
        }

        @Override
        public String detail() {
            return "The supplied catalog model payload does not match the required schema.";
        }
    }
}
