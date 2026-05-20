package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import com.zeromail.core.admin.cat.projection.PerFeatureCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CuratedCatalogQueryService {

    static final String CACHE_KEY = "catalog:etag:v1";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Optional<StringRedisTemplate> redisTemplate;

    public CuratedCatalogQueryService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.redisTemplate = Optional.ofNullable(redisTemplateProvider.getIfAvailable());
    }

    @Transactional(readOnly = true)
    public CuratedCatalogResult getCatalog(String ifNoneMatch) {
        CuratedCatalogResult result =
                redisTemplate
                        .map(StringRedisTemplate::opsForValue)
                        .map(valueOperations -> valueOperations.get(CACHE_KEY))
                        .filter(cached -> !cached.isBlank())
                        .flatMap(this::readCached)
                        .orElseGet(this::loadCatalog);
        if (result.etag().equals(ifNoneMatch)) {
            return result.asNotModified();
        }
        return result;
    }

    public void invalidateCache() {
        redisTemplate.ifPresent(template -> template.delete(CACHE_KEY));
    }

    private CuratedCatalogResult loadCatalog() {
        List<CatalogModelRowWithFeature> rows =
                jdbcTemplate.query(
                        """
                        SELECT binding.feature,
                               model.provider,
                               model.model_id,
                               model.display_name,
                               (defaults.model_id = model.model_id AND defaults.feature = binding.feature) AS default_model,
                               model.is_recommended,
                               model.cost_per_1k_input,
                               model.cost_per_1k_output,
                               model.deprecated_at
                        FROM model_catalog model
                        JOIN provider_catalog provider ON provider.provider = model.provider
                        JOIN feature_binding binding ON binding.model_id = model.model_id
                        LEFT JOIN feature_default_provider defaults ON defaults.feature = binding.feature
                        WHERE provider.enabled = TRUE
                          AND binding.enabled = TRUE
                          AND model.deprecated_at IS NULL
                        ORDER BY binding.feature, default_model DESC, model.is_recommended DESC, model.provider, model.model_id
                        """,
                        (resultSet, rowNumber) ->
                                new CatalogModelRowWithFeature(
                                        Feature.fromId(resultSet.getString("feature")),
                                        new CatalogModelRow(
                                                resultSet.getString("provider"),
                                                resultSet.getString("model_id"),
                                                resultSet.getString("display_name"),
                                                resultSet.getBoolean("default_model"),
                                                resultSet.getBoolean("is_recommended"),
                                                resultSet.getBigDecimal("cost_per_1k_input"),
                                                resultSet.getBigDecimal("cost_per_1k_output"),
                                                null,
                                                0L)));
        Map<Feature, List<CatalogModelRow>> rowsByFeature = new EnumMap<>(Feature.class);
        Map<Feature, String> defaultByFeature = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            rowsByFeature.put(feature, new ArrayList<>());
        }
        for (CatalogModelRowWithFeature row : rows) {
            rowsByFeature.get(row.feature()).add(row.catalogModelRow());
            if (row.catalogModelRow().defaultModel()) {
                defaultByFeature.put(row.feature(), row.catalogModelRow().modelId());
            }
        }
        LinkedHashMap<Feature, PerFeatureCatalog> catalog = new LinkedHashMap<>();
        for (Feature feature : Feature.values()) {
            catalog.put(
                    feature,
                    new PerFeatureCatalog(
                            feature, rowsByFeature.get(feature), defaultByFeature.get(feature)));
        }
        String catalogVersionFingerprint =
                jdbcTemplate
                        .queryForList(
                                """
                                SELECT provider || ':' || catalog_version
                                FROM provider_catalog
                                ORDER BY provider
                                """,
                                String.class)
                        .toString();
        String payloadJson = writeJson(catalog);
        String etag = quote(sha256(catalogVersionFingerprint + payloadJson));
        CuratedCatalogResult result = new CuratedCatalogResult(catalog, etag, false);
        redisTemplate.ifPresent(
                template -> template.opsForValue().set(CACHE_KEY, writeJson(result), CACHE_TTL));
        return result;
    }

    private Optional<CuratedCatalogResult> readCached(String cachedJson) {
        try {
            return Optional.of(objectMapper.readValue(cachedJson, CuratedCatalogResult.class));
        } catch (JacksonException jacksonException) {
            invalidateCache();
            return Optional.empty();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException("Unable to serialize catalog cache", jacksonException);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException("SHA-256 is unavailable", noSuchAlgorithmException);
        }
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private record CatalogModelRowWithFeature(Feature feature, CatalogModelRow catalogModelRow) {}

    public record CuratedCatalogResult(
            Map<Feature, PerFeatureCatalog> catalog, String etag, boolean notModified) {

        public CuratedCatalogResult asNotModified() {
            return new CuratedCatalogResult(catalog, etag, true);
        }
    }
}
