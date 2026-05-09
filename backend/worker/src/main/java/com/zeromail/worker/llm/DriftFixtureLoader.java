package com.zeromail.worker.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class DriftFixtureLoader {

  private static final String GOLDEN_SET_RESOURCE = "/llm/golden-set.json";
  private static final String GOLDEN_BASELINE_RESOURCE = "/llm/golden-baseline.json";

  private final ObjectMapper objectMapper;

  public DriftFixtureLoader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<DriftFixture> loadGoldenSet() {
    try (InputStream inputStream = openResource(GOLDEN_SET_RESOURCE)) {
      return objectMapper.readValue(inputStream, new TypeReference<>() {});
    } catch (IOException ioFailure) {
      throw new UncheckedIOException("Failed to load golden-set.json", ioFailure);
    }
  }

  public Map<String, BaselineEntry> loadBaseline() {
    try (InputStream inputStream = openResource(GOLDEN_BASELINE_RESOURCE)) {
      return objectMapper.readValue(inputStream, new TypeReference<>() {});
    } catch (IOException ioFailure) {
      throw new UncheckedIOException("Failed to load golden-baseline.json", ioFailure);
    }
  }

  private InputStream openResource(String resourcePath) {
    InputStream inputStream = getClass().getResourceAsStream(resourcePath);
    if (inputStream == null) {
      throw new IllegalStateException(resourcePath + " not on classpath");
    }
    return inputStream;
  }

  public record BaselineEntry(String action, String argsJson) {

    public BaselineEntry {
      Objects.requireNonNull(action, "action");
      Objects.requireNonNull(argsJson, "argsJson");
    }
  }
}
