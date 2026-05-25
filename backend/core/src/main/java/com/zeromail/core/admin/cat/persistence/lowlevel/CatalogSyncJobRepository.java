package com.zeromail.core.admin.cat.persistence.lowlevel;

import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import com.zeromail.core.admin.cat.projection.CatalogDiff;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import com.zeromail.core.admin.cat.projection.CatalogSyncJob;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CatalogSyncJobRepository {

    public static final String JOB_TYPE = "CATALOG_SYNC";
    private static final String STEP_FETCH = "FETCH";
    private static final String STEP_FETCHING = "FETCHING";
    private static final String STEP_DIFF_READY = "DIFF_READY";
    private static final String STEP_CONFIRMED = "CONFIRMED";
    private static final String STEP_CANCELLED = "CANCELLED";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CatalogSyncJobRepository(
            NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Transactional
    public UUID insertFetchJob(UUID jobId, LlmProvider provider, UUID actorId) {
        jdbcTemplate.update(
                """
                INSERT INTO processing_job(id, job_type, status, payload_json)
                VALUES (:jobId, :jobType, 'PENDING', CAST(:stateJson AS jsonb))
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("jobType", JOB_TYPE)
                        .addValue(
                                "stateJson",
                                writeJson(
                                        Map.of(
                                                "provider",
                                                provider.id(),
                                                "actorId",
                                                actorId.toString(),
                                                "jobId",
                                                jobId.toString(),
                                                "step",
                                                STEP_FETCH))));
        return jobId;
    }

    public Optional<UUID> findActiveJobWithin(LlmProvider provider, Instant since) {
        List<UUID> jobIds =
                jdbcTemplate.query(
                        """
                        SELECT id
                        FROM processing_job
                        WHERE job_type = :jobType
                          AND payload_json->>'provider' = :provider
                          AND status IN ('PENDING','PROCESSING')
                          AND created_at >= :since
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                        new MapSqlParameterSource()
                                .addValue("jobType", JOB_TYPE)
                                .addValue("provider", provider.id())
                                .addValue("since", since),
                        (resultSet, rowNumber) -> UUID.fromString(resultSet.getString("id")));
        return jobIds.stream().findFirst();
    }

    @Transactional
    public Optional<CatalogSyncJob> claimNextFetchJob(Duration lockDuration) {
        List<CatalogSyncJob> claimedJobs =
                jdbcTemplate.query(
                        """
                        UPDATE processing_job
                        SET status = 'PROCESSING',
                            locked_at = NOW(),
                            locked_until = NOW() + (:lockSeconds * INTERVAL '1 second'),
                            updated_at = NOW(),
                            payload_json = jsonb_set(payload_json, '{step}', '"FETCHING"', true)
                        WHERE id = (
                            SELECT id
                            FROM processing_job
                            WHERE job_type = :jobType
                              AND status = 'PENDING'
                              AND payload_json->>'step' = 'FETCH'
                              AND (locked_until IS NULL OR locked_until < NOW())
                            ORDER BY created_at
                            FOR UPDATE SKIP LOCKED
                            LIMIT 1
                        )
                        RETURNING id, payload_json->>'provider' AS provider, status, created_at, updated_at, payload_json
                        """,
                        new MapSqlParameterSource()
                                .addValue("jobType", JOB_TYPE)
                                .addValue("lockSeconds", lockDuration.toSeconds()),
                        this::mapJob);
        return claimedJobs.stream().findFirst();
    }

    @Transactional
    public void markDiffReady(UUID jobId, CatalogDiff catalogDiff) {
        jdbcTemplate.update(
                """
                UPDATE processing_job
                SET status = 'PENDING',
                    locked_at = NULL,
                    locked_until = NULL,
                    updated_at = NOW(),
                    payload_json = payload_json || CAST(:statePatchJson AS jsonb)
                WHERE id = :jobId
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue(
                                "statePatchJson",
                                writeJson(Map.of("step", STEP_DIFF_READY, "diff", catalogDiff))));
    }

    @Transactional
    public void markFailed(UUID jobId, String reason) {
        jdbcTemplate.update(
                """
                UPDATE processing_job
                SET status = 'FAILED',
                    locked_at = NULL,
                    locked_until = NULL,
                    last_failed_at = NOW(),
                    updated_at = NOW(),
                    payload_json = payload_json || CAST(:statePatchJson AS jsonb)
                WHERE id = :jobId
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue(
                                "statePatchJson",
                                writeJson(
                                        Map.of(
                                                "step",
                                                "FAILED",
                                                "failureReason",
                                                safeReason(reason)))));
    }

    @Transactional
    public void markConfirmed(UUID jobId) {
        markCompleted(jobId, STEP_CONFIRMED);
    }

    @Transactional
    public void markCancelled(UUID jobId) {
        markCompleted(jobId, STEP_CANCELLED);
    }

    @Transactional
    public int abandonStaleProcessingJobs(Duration staleAfter) {
        return jdbcTemplate.update(
                """
                UPDATE processing_job
                SET status = 'FAILED',
                    locked_at = NULL,
                    locked_until = NULL,
                    last_failed_at = NOW(),
                    updated_at = NOW(),
                    payload_json = payload_json || '{"step":"ABANDONED","failureReason":"LOCK_TIMEOUT"}'::jsonb
                WHERE job_type = :jobType
                  AND status = 'PROCESSING'
                  AND locked_at IS NOT NULL
                  AND locked_at < NOW() - (:staleSeconds * INTERVAL '1 second')
                """,
                new MapSqlParameterSource()
                        .addValue("jobType", JOB_TYPE)
                        .addValue("staleSeconds", staleAfter.toSeconds()));
    }

    public Optional<CatalogSyncJob> findJob(UUID jobId) {
        List<CatalogSyncJob> jobs =
                jdbcTemplate.query(
                        """
                        SELECT id, payload_json->>'provider' AS provider, status, created_at, updated_at, payload_json
                        FROM processing_job
                        WHERE id = :jobId
                          AND job_type = :jobType
                        """,
                        new MapSqlParameterSource()
                                .addValue("jobId", jobId)
                                .addValue("jobType", JOB_TYPE),
                        this::mapJob);
        return jobs.stream().findFirst();
    }

    public CatalogDiff diffForJob(UUID jobId) {
        CatalogSyncJob catalogSyncJob =
                findJob(jobId).orElseThrow(() -> new CatalogSyncJobNotFoundException(jobId));
        JsonNode diffNode = catalogSyncJob.stepStateJson().path("diff");
        return new CatalogDiff(
                modelRows(diffNode.path("added")),
                modelRows(diffNode.path("removed")),
                modelRows(diffNode.path("changed")));
    }

    public boolean isDiffReady(UUID jobId) {
        return findJob(jobId)
                .map(job -> STEP_DIFF_READY.equals(job.stepStateJson().path("step").asString()))
                .orElse(false);
    }

    private void markCompleted(UUID jobId, String step) {
        jdbcTemplate.update(
                """
                UPDATE processing_job
                SET status = 'COMPLETED',
                    locked_at = NULL,
                    locked_until = NULL,
                    completed_at = NOW(),
                    updated_at = NOW(),
                    payload_json = jsonb_set(payload_json, '{step}', CAST(:stepJson AS jsonb), true)
                WHERE id = :jobId
                """,
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("stepJson", "\"" + step + "\""));
    }

    private CatalogSyncJob mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        String payload = resultSet.getString("payload_json");
        return new CatalogSyncJob(
                UUID.fromString(resultSet.getString("id")),
                LlmProvider.fromId(resultSet.getString("provider")),
                resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                readTree(payload));
    }

    private List<CatalogModelRow> modelRows(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return List.of();
        }
        java.util.ArrayList<CatalogModelRow> rows = new java.util.ArrayList<>();
        for (JsonNode rowNode : arrayNode) {
            rows.add(
                    new CatalogModelRow(
                            rowNode.path("provider").asString(),
                            rowNode.path("modelId").asString(),
                            rowNode.path("displayName").asString(),
                            rowNode.path("defaultModel").asBoolean(false),
                            rowNode.path("recommended").asBoolean(false),
                            null,
                            null,
                            ModelVerificationStatus.UNTESTED,
                            null,
                            rowNode.path("pinnedTenantCount").asLong(0)));
        }
        return rows;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "Catalog sync job state is not valid JSON", jacksonException);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "Unable to serialize catalog sync job state", jacksonException);
        }
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "UNKNOWN";
        }
        return reason.length() > 64 ? reason.substring(0, 64) : reason;
    }

    public static class CatalogSyncJobNotFoundException extends AdminBusinessException {

        public CatalogSyncJobNotFoundException(UUID jobId) {
            super("Catalog sync job not found: " + jobId);
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.NOT_FOUND;
        }

        @Override
        public String errorCode() {
            return "error.admin.catalog_sync_job_not_found";
        }

        @Override
        public String logEvent() {
            return "admin_catalog_sync_job_not_found";
        }

        @Override
        public String detail() {
            return "The requested catalog sync job could not be located.";
        }
    }
}
