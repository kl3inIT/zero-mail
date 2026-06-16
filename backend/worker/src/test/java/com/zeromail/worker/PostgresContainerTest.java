package com.zeromail.worker;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.worker.test.MockGmailHistoryServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = PostgresContainerTest.WorkerTestApplication.class)
public abstract class PostgresContainerTest {

    protected static final PostgreSQLContainer<?> POSTGRES;
    protected static final MockGmailHistoryServer GMAIL;

    @Autowired private RefreshTokenCipher cipher;

    static {
        POSTGRES =
                new PostgreSQLContainer<>("postgres:18.4")
                        .withDatabaseName("zeromail_worker_test")
                        // Mirror backend/core: bump max_connections so cached @SpringBootTest
                        // contexts don't exhaust the default cap of 100 (each context's
                        // HikariCP pool is capped at 30 below).
                        .withCommand("postgres", "-c", "max_connections=500");
        POSTGRES.start();
        GMAIL = new MockGmailHistoryServer();
        try {
            GMAIL.start();
        } catch (IOException ioException) {
            throw new ExceptionInInitializerError(ioException);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        dynamicPropertyRegistry.add("spring.datasource.username", POSTGRES::getUsername);
        dynamicPropertyRegistry.add("spring.datasource.password", POSTGRES::getPassword);
        // Cap HikariCP per-context so the cached test-context graph doesn't exhaust the
        // shared container's connection pool. 30 leaves headroom for concurrent-load tests
        // while still letting ~15 cached contexts fit under max_connections=500.
        dynamicPropertyRegistry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        dynamicPropertyRegistry.add("spring.datasource.hikari.minimum-idle", () -> "0");
        dynamicPropertyRegistry.add("spring.liquibase.enabled", () -> "true");
        dynamicPropertyRegistry.add(
                "spring.liquibase.change-log",
                () -> "classpath:db/changelog/db.changelog-master.yaml");
        dynamicPropertyRegistry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        dynamicPropertyRegistry.add(
                "spring.security.oauth2.client.registration.google.client-id",
                () -> "test-google-client");
        dynamicPropertyRegistry.add(
                "spring.security.oauth2.client.registration.google.client-secret",
                () -> "test-google-secret");
        dynamicPropertyRegistry.add(
                "zero-mail.crypto.refresh-token-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        // Inbox-projection KEK + sender-hash key: the worker test context scans com.zeromail.core,
        // which now instantiates InboxProjectionCipher (core.inbox.usecases). Without these the
        // CryptoProperties binding falls back to application.yml's ${INBOX_PROJECTION_*:?} and the
        // cipher's Base64 decode fails. Mirrors ApiPostgresTestBase.
        dynamicPropertyRegistry.add(
                "zero-mail.crypto.inbox-projection-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        dynamicPropertyRegistry.add(
                "zero-mail.crypto.inbox-projection-sender-hash-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        dynamicPropertyRegistry.add(
                "zero-mail.admin.audit.hmac-kek-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        dynamicPropertyRegistry.add(
                "zero-mail.worker.gmail.pubsub.topic-name", () -> "projects/test/topics/gmail");
        dynamicPropertyRegistry.add("zero-mail.scheduling.enabled", () -> "false");
        dynamicPropertyRegistry.add("zero-mail.gmail.api-root-url", GMAIL::baseUrl);
        dynamicPropertyRegistry.add(
                "zero-mail.gmail.oauth-token-url", () -> GMAIL.baseUrl() + "token");
        // Spring AI starters are on the worker classpath in Phase 02C. Worker tests do not exercise
        // the gateway adapter yet, but auto-configuration still requires placeholder keys.
        dynamicPropertyRegistry.add("spring.ai.openai.api-key", () -> "test-openai-key");
        dynamicPropertyRegistry.add("spring.ai.anthropic.api-key", () -> "test-anthropic-key");
        dynamicPropertyRegistry.add(
                "zero-mail.llm.platform.api-key", () -> "test-platform-llm-key");
        dynamicPropertyRegistry.add(
                "zero-mail.notification.email.resend.api-key", () -> "test-resend-key");
        dynamicPropertyRegistry.add(
                "zero-mail.notification.email.from-address", () -> "notifications@zero-mail.test");
        dynamicPropertyRegistry.add(
                "zero-mail.notification.app-base-url", () -> "https://zero-mail.test");
    }

    protected byte[] encryptedRefreshToken(UUID tenantId) {
        return cipher.encrypt(
                "worker-refresh-token".getBytes(StandardCharsets.UTF_8), tenantId.toString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = {"com.zeromail.worker", "com.zeromail.core"},
            excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(
                        type = FilterType.CUSTOM,
                        classes = AutoConfigurationExcludeFilter.class),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = ZeroMailWorkerApplication.class)
            })
    @ConfigurationPropertiesScan(basePackages = "com.zeromail")
    @EntityScan(basePackages = "com.zeromail.core")
    @EnableJpaRepositories(basePackages = "com.zeromail.core")
    static class WorkerTestApplication {

        @Bean(name = "taskScheduler")
        TaskScheduler taskScheduler() {
            return new NoopTaskScheduler();
        }
    }

    private static final class NoopTaskScheduler implements TaskScheduler {

        @Override
        public @NonNull ScheduledFuture<?> schedule(
                @NonNull Runnable task, @NonNull Trigger trigger) {
            return NoopScheduledFuture.INSTANCE;
        }

        @Override
        public @NonNull ScheduledFuture<?> schedule(
                @NonNull Runnable task, @NonNull Instant startTime) {
            return NoopScheduledFuture.INSTANCE;
        }

        @Override
        public @NonNull ScheduledFuture<?> scheduleAtFixedRate(
                @NonNull Runnable task, @NonNull Instant startTime, @NonNull Duration period) {
            return NoopScheduledFuture.INSTANCE;
        }

        @Override
        public @NonNull ScheduledFuture<?> scheduleAtFixedRate(
                @NonNull Runnable task, @NonNull Duration period) {
            return NoopScheduledFuture.INSTANCE;
        }

        @Override
        public @NonNull ScheduledFuture<?> scheduleWithFixedDelay(
                @NonNull Runnable task, @NonNull Instant startTime, @NonNull Duration delay) {
            return NoopScheduledFuture.INSTANCE;
        }

        @Override
        public @NonNull ScheduledFuture<?> scheduleWithFixedDelay(
                @NonNull Runnable task, @NonNull Duration delay) {
            return NoopScheduledFuture.INSTANCE;
        }
    }

    private static final class NoopScheduledFuture implements ScheduledFuture<Object> {

        private static final NoopScheduledFuture INSTANCE = new NoopScheduledFuture();

        @Override
        public long getDelay(@NonNull TimeUnit unit) {
            return Long.MAX_VALUE;
        }

        @Override
        public int compareTo(@NonNull Delayed other) {
            return Long.compare(
                    getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return true;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, @NonNull TimeUnit unit) {
            return null;
        }
    }
}
