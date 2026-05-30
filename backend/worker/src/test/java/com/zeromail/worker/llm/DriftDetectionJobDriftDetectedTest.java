package com.zeromail.worker.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.ToolCallResult;
import com.zeromail.worker.config.DriftProperties;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class DriftDetectionJobDriftDetectedTest {

    private static final String FIXED_TENANT_ID = "00000000-0000-0000-0000-000000000000";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DriftFixtureLoader loader = new DriftFixtureLoader(objectMapper);

    @Test
    void drift_detected_on_action_mismatch() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        answerWithActionMismatch(llmGateway);
        DriftDetectionJob driftDetectionJob = enabledJob(llmGateway);

        driftDetectionJob.run();

        assertThat(driftDetectionJob.lastRunDriftCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void drift_detected_on_args_levenshtein_over_20pct() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        answerWithArgumentMutation(llmGateway);
        DriftDetectionJob driftDetectionJob = enabledJob(llmGateway);

        driftDetectionJob.run();

        assertThat(driftDetectionJob.lastRunDriftCount()).isGreaterThanOrEqualTo(1);
    }

    private DriftDetectionJob enabledJob(LlmGateway llmGateway) {
        return new DriftDetectionJob(
                llmGateway, loader, objectMapper, new DriftProperties(true, FIXED_TENANT_ID, 20));
    }

    private void answerWithActionMismatch(LlmGateway llmGateway) {
        Map<String, DriftFixture> fixturesByPrompt =
                loader.loadGoldenSet().stream()
                        .collect(Collectors.toMap(DriftFixture::prompt, fixture -> fixture));
        Map<String, DriftFixtureLoader.BaselineEntry> baseline = loader.loadBaseline();

        when(llmGateway.driftCheck(anyString()))
                .thenAnswer(
                        invocation -> {
                            DriftFixture fixture =
                                    fixturesByPrompt.get(invocation.getArgument(0, String.class));
                            if ("stripe-receipt-001".equals(fixture.id())) {
                                return new ToolCallResult(Action.ARCHIVE, Map.of());
                            }
                            DriftFixtureLoader.BaselineEntry baselineEntry =
                                    baseline.get(fixture.id());
                            return new ToolCallResult(
                                    Action.fromFunctionName(baselineEntry.action()),
                                    parseArgs(baselineEntry.argsJson()));
                        });
    }

    private void answerWithArgumentMutation(LlmGateway llmGateway) {
        Map<String, DriftFixture> fixturesByPrompt =
                loader.loadGoldenSet().stream()
                        .collect(Collectors.toMap(DriftFixture::prompt, fixture -> fixture));
        Map<String, DriftFixtureLoader.BaselineEntry> baseline = loader.loadBaseline();

        when(llmGateway.driftCheck(anyString()))
                .thenAnswer(
                        invocation -> {
                            DriftFixture fixture =
                                    fixturesByPrompt.get(invocation.getArgument(0, String.class));
                            DriftFixtureLoader.BaselineEntry baselineEntry =
                                    baseline.get(fixture.id());
                            if ("stripe-receipt-001".equals(fixture.id())) {
                                return new ToolCallResult(
                                        Action.fromFunctionName(baselineEntry.action()),
                                        Map.of(
                                                "value",
                                                "Stripe Receipts and Confirmations Unexpected Drift"));
                            }
                            return new ToolCallResult(
                                    Action.fromFunctionName(baselineEntry.action()),
                                    parseArgs(baselineEntry.argsJson()));
                        });
    }

    private Map<String, Object> parseArgs(String argsJson) {
        return objectMapper.readValue(argsJson, new TypeReference<>() {});
    }
}
