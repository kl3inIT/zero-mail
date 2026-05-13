package com.zeromail.core.aiEval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.domain.ThreadReplyStatus;
import com.zeromail.core.thread.persistence.ThreadReplyStatusEntity;
import com.zeromail.core.thread.persistence.ThreadReplyStatusRepository;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.thread.usecases.ThreadReplyClassificationInput;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ClassifierAccuracyEvalTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-00000005b777");
    private static final Instant CLASSIFIED_AT = Instant.parse("2026-05-13T00:00:00Z");
    private static final Pattern NON_SYNTHETIC_EMAIL_PATTERN =
            Pattern.compile(
                    "[A-Za-z0-9._%+-]+@(?!synthetic\\.test\\b)[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    @Test
    void dim7_classifier_scores_at_least_85_percent_without_one_direction_skew() throws Exception {
        List<ClassifierFixture> fixtures = loadFixtures();
        int correct = 0;
        int toReplyExpected = 0;
        int awaitingExpected = 0;
        int toReplyMisses = 0;
        int awaitingMisses = 0;

        for (ClassifierFixture fixture : fixtures) {
            ThreadReplyStatus classifiedStatus = classify(fixture);
            if (classifiedStatus.bucket() == fixture.expectedBucket()) {
                correct++;
            } else if (fixture.expectedBucket() == ThreadReplyBucket.TO_REPLY) {
                toReplyMisses++;
            } else if (fixture.expectedBucket() == ThreadReplyBucket.AWAITING_THEIR_REPLY) {
                awaitingMisses++;
            }
            if (fixture.expectedBucket() == ThreadReplyBucket.TO_REPLY) {
                toReplyExpected++;
            }
            if (fixture.expectedBucket() == ThreadReplyBucket.AWAITING_THEIR_REPLY) {
                awaitingExpected++;
            }
        }

        double accuracy = (double) correct / fixtures.size();

        assertThat(fixtures).hasSizeBetween(20, 30);
        assertThat(fixtures.stream().filter(ClassifierFixture::edgeCase).count())
                .as("held-out set must include at least five non-trivial edge cases")
                .isGreaterThanOrEqualTo(5);
        assertThat(toReplyExpected).isGreaterThanOrEqualTo(8);
        assertThat(awaitingExpected).isGreaterThanOrEqualTo(6);
        assertThat(Math.abs(toReplyMisses - awaitingMisses))
                .as("misses must not skew in only one direction")
                .isLessThanOrEqualTo(2);
        assertThat(accuracy).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void classifier_fixtures_are_synthetic_only() throws Exception {
        Path fixtureDirectory = resourceDirectory("fixtures/classifier");
        String fixtureCorpus =
                Files.list(fixtureDirectory)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .map(this::readUnchecked)
                        .reduce("", (left, right) -> left + "\n" + right);

        assertThat(fixtureCorpus).doesNotContain("gmail.com", "googlemail.com", "outlook.com");
        assertThat(NON_SYNTHETIC_EMAIL_PATTERN.matcher(fixtureCorpus).find()).isFalse();
    }

    private static ThreadReplyStatus classify(ClassifierFixture fixture) {
        ThreadReplyStatusRepository threadReplyStatusRepository =
                mock(ThreadReplyStatusRepository.class);
        when(threadReplyStatusRepository.findByGmailThreadId(fixture.id()))
                .thenReturn(java.util.Optional.empty());
        when(threadReplyStatusRepository.save(any(ThreadReplyStatusEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ClassifyThreadReplyStatusService classifyThreadReplyStatusService =
                new ClassifyThreadReplyStatusService(
                        threadReplyStatusRepository, Clock.fixed(CLASSIFIED_AT, ZoneOffset.UTC));

        return classifyThreadReplyStatusService.classify(
                new ThreadReplyClassificationInput(
                        TENANT_ID,
                        fixture.id(),
                        fixture.id() + "-last-message",
                        fixture.lastMessageFromTenant(),
                        fixture.threadHasSentLabel(),
                        fixture.hasZeroMailDraft(),
                        fixture.hasZeroMailDraft() ? fixture.id() + "-draft" : null,
                        fixture.lastMessageIsAutoReply()));
    }

    private static List<ClassifierFixture> loadFixtures() throws IOException, URISyntaxException {
        try (var fixturePaths = Files.list(resourceDirectory("fixtures/classifier"))) {
            return fixturePaths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(ClassifierAccuracyEvalTest::readFixtureUnchecked)
                    .toList();
        }
    }

    private static ClassifierFixture readFixtureUnchecked(Path path) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = OBJECT_MAPPER.readValue(path.toFile(), Map.class);
            return new ClassifierFixture(
                    string(json, "id"),
                    ThreadReplyBucket.fromId(string(json, "expectedBucket")),
                    bool(json, "lastMessageFromTenant"),
                    bool(json, "threadHasSentLabel"),
                    bool(json, "hasZeroMailDraft"),
                    bool(json, "lastMessageIsAutoReply"),
                    bool(json, "edgeCase"));
        } catch (RuntimeException parsingFailure) {
            throw new IllegalStateException(
                    "Unable to load classifier fixture " + path, parsingFailure);
        }
    }

    private String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ioException) {
            throw new IllegalStateException("Unable to read fixture " + path, ioException);
        }
    }

    private static Path resourceDirectory(String relativePath) throws URISyntaxException {
        return Path.of(
                java.util.Objects.requireNonNull(
                                ClassifierAccuracyEvalTest.class
                                        .getClassLoader()
                                        .getResource(relativePath),
                                relativePath + " resource directory must exist")
                        .toURI());
    }

    private static String string(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Fixture field " + key + " must be text");
        }
        return text;
    }

    private static boolean bool(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException("Fixture field " + key + " must be boolean");
        }
        return bool;
    }

    private record ClassifierFixture(
            String id,
            ThreadReplyBucket expectedBucket,
            boolean lastMessageFromTenant,
            boolean threadHasSentLabel,
            boolean hasZeroMailDraft,
            boolean lastMessageIsAutoReply,
            boolean edgeCase) {}
}
