package com.zeromail.worker.llm;

import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.ToolCallResult;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.worker.config.ZeroMailLlmDriftProperties;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class DriftDetectionJob {

    private static final Logger log = LoggerFactory.getLogger(DriftDetectionJob.class);
    private static final LevenshteinDistance LEVENSHTEIN_DISTANCE =
            LevenshteinDistance.getDefaultInstance();

    private final LlmGateway llmGateway;
    private final DriftFixtureLoader fixtureLoader;
    private final ObjectMapper objectMapper;
    private final ZeroMailLlmDriftProperties driftProperties;
    private volatile int lastRunDriftCount = -1;

    public DriftDetectionJob(
            LlmGateway llmGateway,
            DriftFixtureLoader fixtureLoader,
            ObjectMapper objectMapper,
            ZeroMailLlmDriftProperties driftProperties) {
        this.llmGateway = llmGateway;
        this.fixtureLoader = fixtureLoader;
        this.objectMapper = objectMapper;
        this.driftProperties = driftProperties;
    }

    @Scheduled(cron = "0 0 6 * * *")
    @SchedulerLock(name = "llmDriftDetectionJob", lockAtLeastFor = "PT30S", lockAtMostFor = "PT10M")
    public void scheduledTick() {
        if (!driftProperties.enabled()) {
            log.debug("event=drift_check_skipped reason=disabled");
            return;
        }
        run();
    }

    public void run() {
        List<DriftFixture> fixtures = fixtureLoader.loadGoldenSet();
        Map<String, DriftFixtureLoader.BaselineEntry> baseline = fixtureLoader.loadBaseline();

        int total = 0;
        int drifted = 0;
        for (DriftFixture fixture : fixtures) {
            total++;
            ToolCallResult result =
                    ScopedValue.where(TenantContext.TENANT, driftProperties.fixedTenantId())
                            .call(() -> llmGateway.driftCheck(fixture.prompt()));
            DriftFixtureLoader.BaselineEntry baselineEntry = baseline.get(fixture.id());
            if (baselineEntry == null) {
                continue;
            }
            if (hasActionDrift(result, baselineEntry) || hasArgumentDrift(result, baselineEntry)) {
                drifted++;
            }
        }

        lastRunDriftCount = drifted;
        log.info("event=drift_check_run total={} drifted={}", total, drifted);
    }

    public int lastRunDriftCount() {
        return lastRunDriftCount;
    }

    private boolean hasActionDrift(
            ToolCallResult result, DriftFixtureLoader.BaselineEntry baselineEntry) {
        return !result.action().functionName().equals(baselineEntry.action());
    }

    private boolean hasArgumentDrift(
            ToolCallResult result, DriftFixtureLoader.BaselineEntry baselineEntry) {
        String resultArgsJson = serializeArgs(result.args());
        int distance = LEVENSHTEIN_DISTANCE.apply(resultArgsJson, baselineEntry.argsJson());
        int threshold =
                baselineEntry.argsJson().length() * driftProperties.thresholdPercent() / 100;
        return distance > threshold;
    }

    private String serializeArgs(Map<String, Object> args) {
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(args));
        } catch (JacksonException _) {
            throw new IllegalStateException("Failed to serialize drift result arguments");
        }
    }
}
