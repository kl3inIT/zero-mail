package com.zeromail.worker.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class DriftFixtureLoaderTest {

  private static final Set<String> ALLOWED_ACTIONS = Set.of("label", "archive", "save_draft");
  private static final Set<String> FORBIDDEN_CONSUMER_DOMAINS =
      Set.of("gmail.com", "outlook.com", "hotmail.com", "yahoo.com", "icloud.com");

  private final DriftFixtureLoader loader = new DriftFixtureLoader(new ObjectMapper());

  @Test
  void loads_golden_set_with_at_least_20_fixtures() {
    assertThat(loader.loadGoldenSet()).hasSizeGreaterThanOrEqualTo(20);
  }

  @Test
  void fixtures_have_required_fields() {
    assertThat(loader.loadGoldenSet())
        .allSatisfy(
            fixture -> {
              assertThat(fixture.id()).isNotBlank();
              assertThat(fixture.subject()).isNotBlank();
              assertThat(fixture.from()).isNotBlank();
              assertThat(fixture.htmlBody()).isNotBlank();
              assertThat(fixture.expectedAction()).isNotBlank();
              assertThat(fixture.expectedArgs()).isNotNull();
            });
  }

  @Test
  void expected_actions_only_in_allow_list() {
    assertThat(loader.loadGoldenSet())
        .allSatisfy(fixture -> assertThat(ALLOWED_ACTIONS).contains(fixture.expectedAction()));
  }

  @Test
  void contains_required_categories() {
    List<String> fixtureIds =
        loader.loadGoldenSet().stream().map(DriftFixture::id).collect(Collectors.toList());

    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("stripe-receipt"));
    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("github-pr"));
    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("calendar-invite"));
    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("newsletter"));
    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("plain-text"));
    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("multilingual-en-vi"));
    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("unicode-tag-injection"));
    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("hidden-text-injection"));
    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("generic-transactional"));
    assertThat(fixtureIds).anyMatch(fixtureId -> fixtureId.startsWith("html-newsletter-tracking"));
  }

  @Test
  void fixtures_contain_no_real_pii_email_domains() {
    assertThat(loader.loadGoldenSet())
        .allSatisfy(
            fixture -> {
              String domain = fixture.from().substring(fixture.from().indexOf('@') + 1);
              assertThat(FORBIDDEN_CONSUMER_DOMAINS).doesNotContain(domain);
            });
  }

  @Test
  void loads_baseline() {
    List<DriftFixture> fixtures = loader.loadGoldenSet();
    Map<String, DriftFixtureLoader.BaselineEntry> baseline = loader.loadBaseline();

    assertThat(baseline).isNotEmpty();
    assertThat(baseline.keySet())
        .containsAll(fixtures.stream().map(DriftFixture::id).collect(Collectors.toSet()));
  }
}
