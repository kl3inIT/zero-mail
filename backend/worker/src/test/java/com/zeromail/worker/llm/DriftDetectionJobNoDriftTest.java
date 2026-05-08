package com.zeromail.worker.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.zeromail.core.llm.model.Action;
import com.zeromail.core.llm.model.ToolCallResult;
import com.zeromail.core.llm.service.LlmGateway;
import com.zeromail.worker.config.ZeroMailLlmDriftProperties;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class DriftDetectionJobNoDriftTest {

  private static final String FIXED_TENANT_ID = "00000000-0000-0000-0000-000000000000";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DriftFixtureLoader loader = new DriftFixtureLoader(objectMapper);

  @Test
  void no_drift_when_outputs_match_baseline() {
    LlmGateway llmGateway = mock(LlmGateway.class);
    answerWithBaseline(llmGateway);
    DriftDetectionJob driftDetectionJob = enabledJob(llmGateway);

    String formattedMessages = captureJobLog(driftDetectionJob::run);

    assertThat(driftDetectionJob.lastRunDriftCount()).isZero();
    assertThat(formattedMessages).contains("event=drift_check_run total=20 drifted=0");
  }

  @Test
  void enabled_false_skips_run() {
    LlmGateway llmGateway = mock(LlmGateway.class);
    DriftDetectionJob driftDetectionJob =
        new DriftDetectionJob(
            llmGateway,
            loader,
            objectMapper,
            new ZeroMailLlmDriftProperties(false, FIXED_TENANT_ID, 20));

    driftDetectionJob.scheduledTick();

    verifyNoInteractions(llmGateway);
    assertThat(driftDetectionJob.lastRunDriftCount()).isEqualTo(-1);
  }

  @Test
  void emits_metadata_only_log() {
    LlmGateway llmGateway = mock(LlmGateway.class);
    answerWithBaseline(llmGateway);
    DriftDetectionJob driftDetectionJob = enabledJob(llmGateway);

    String formattedMessages = captureJobLog(driftDetectionJob::run);

    assertThat(formattedMessages).contains("event=drift_check_run total=20 drifted=0");
    for (DriftFixture fixture : loader.loadGoldenSet()) {
      assertThat(formattedMessages)
          .doesNotContain(fixture.id(), fixture.subject(), fixture.from(), fixture.htmlBody());
    }
  }

  private DriftDetectionJob enabledJob(LlmGateway llmGateway) {
    return new DriftDetectionJob(
        llmGateway,
        loader,
        objectMapper,
        new ZeroMailLlmDriftProperties(true, FIXED_TENANT_ID, 20));
  }

  private void answerWithBaseline(LlmGateway llmGateway) {
    Map<String, DriftFixture> fixturesByPrompt =
        loader.loadGoldenSet().stream()
            .collect(Collectors.toMap(DriftFixture::prompt, fixture -> fixture));
    Map<String, DriftFixtureLoader.BaselineEntry> baseline = loader.loadBaseline();

    when(llmGateway.driftCheck(anyString()))
        .thenAnswer(
            invocation -> {
              String prompt = invocation.getArgument(0, String.class);
              DriftFixture fixture = fixturesByPrompt.get(prompt);
              DriftFixtureLoader.BaselineEntry baselineEntry = baseline.get(fixture.id());
              return new ToolCallResult(
                  Action.fromFunctionName(baselineEntry.action()),
                  parseArgs(baselineEntry.argsJson()));
            });
  }

  private Map<String, Object> parseArgs(String argsJson) {
    return objectMapper.readValue(argsJson, new TypeReference<>() {});
  }

  private static String captureJobLog(Runnable jobRun) {
    Logger driftLogger = (Logger) LoggerFactory.getLogger(DriftDetectionJob.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    driftLogger.addAppender(listAppender);
    try {
      jobRun.run();
    } finally {
      driftLogger.detachAppender(listAppender);
    }
    return listAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce(
            "", (combinedMessages, formattedMessage) -> combinedMessages + "\n" + formattedMessage);
  }
}
