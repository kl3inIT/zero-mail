package com.zeromail.core.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for integration tests that require a real Postgres 17 instance with the
 * Liquibase schema applied. Subclasses inherit the container plus dynamic datasource
 * properties; Spring Boot's Liquibase auto-config does the schema push on context start.
 *
 * Singleton container pattern: one container started once at static init, shared across all
 * subclass test classes for the JVM lifetime. Avoids per-class container recycling that
 * leaves Spring's cached test context bound to a now-stale dynamic port.
 *
 * Requires Docker on the host.
 */
@SpringBootTest(classes = CoreTestApplication.class)
public abstract class PostgresContainerTest {

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:17.6").withDatabaseName("zeromail_test");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.liquibase.enabled", () -> "true");
        r.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.yaml");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.session.store-type", () -> "none");
        r.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration");
    }
}
