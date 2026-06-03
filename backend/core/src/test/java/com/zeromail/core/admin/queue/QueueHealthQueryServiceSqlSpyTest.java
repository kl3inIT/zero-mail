package com.zeromail.core.admin.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.queue.persistence.lowlevel.QueueHealthReadRepository;
import com.zeromail.core.admin.queue.projection.JobPage;
import com.zeromail.core.admin.queue.projection.QueueHealthSnapshot;
import com.zeromail.core.admin.queue.usecases.QueueHealthQueryService;
import com.zeromail.core.support.PostgresContainerTest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * R-8E-H4 gate: spy on every SQL string emitted during {@link QueueHealthQueryService#snapshot()},
 * {@link QueueHealthQueryService#jobsPage(String, String, String, int)}, and {@link
 * QueueHealthQueryService#jobDetail(java.util.UUID)} and assert NONE references {@code
 * payload_json} or {@code payloadJson}.
 *
 * <p>This is the runtime contract test that complements the static grep gate and the {@link
 * com.zeromail.core.admin.arch.AdminPathBodyBanTest} field-name regex.
 */
class QueueHealthQueryServiceSqlSpyTest extends PostgresContainerTest {

    @Autowired private DataSource dataSource;

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanProcessingJob() {
        jdbcTemplate.execute("DELETE FROM processing_job");
        jdbcTemplate.update(
                "INSERT INTO processing_job(job_type, status, attempts) VALUES "
                        + "('SPY_FIXTURE_JOB', 'DEAD_LETTER', 3)");
    }

    @Test
    void snapshot_jobs_and_detail_never_select_payload_json() {
        List<String> capturedSql = Collections.synchronizedList(new ArrayList<>());
        DataSource spyingDataSource = new SqlCapturingDataSource(dataSource, capturedSql);
        QueueHealthReadRepository queueHealthReadRepository =
                new QueueHealthReadRepository(new NamedParameterJdbcTemplate(spyingDataSource));
        QueueHealthQueryService queueHealthQueryService =
                new QueueHealthQueryService(queueHealthReadRepository, Clock.systemUTC());

        QueueHealthSnapshot snapshot = queueHealthQueryService.snapshot();
        // Exercise every unified-job-list SQL path: unfiltered, status+jobType filtered, the
        // synthetic SCHEDULED filter, and per-job detail — each must avoid payload_json too.
        JobPage jobPage = queueHealthQueryService.jobsPage(null, null, null, 25);
        queueHealthQueryService.jobsPage("SCHEDULED", "CATALOG_SYNC", null, 25);
        if (!jobPage.rows().isEmpty()) {
            queueHealthQueryService.jobDetail(jobPage.rows().get(0).jobId());
        }

        assertThat(snapshot).isNotNull();
        assertThat(jobPage).isNotNull();

        assertThat(capturedSql).as("at least one SQL statement was emitted").isNotEmpty();
        for (String emittedSql : capturedSql) {
            String lowered = emittedSql.toLowerCase();
            assertThat(lowered)
                    .as("emitted SQL must not reference payload_json: %s", emittedSql)
                    .doesNotContain("payload_json")
                    .doesNotContain("payloadjson");
        }
    }

    /**
     * Lightweight wrapping DataSource that records every SQL string passed to {@code
     * prepareStatement} / {@code createStatement} on the returned Connection proxies.
     */
    private static final class SqlCapturingDataSource extends DelegatingDataSource {

        private final List<String> capturedSql;

        private SqlCapturingDataSource(DataSource delegate, List<String> capturedSql) {
            super(delegate);
            this.capturedSql = capturedSql;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection delegateConnection = getTargetDataSource().getConnection();
            return wrap(delegateConnection);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection delegateConnection = getTargetDataSource().getConnection(username, password);
            return wrap(delegateConnection);
        }

        private Connection wrap(Connection delegateConnection) {
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            new SqlCapturingConnectionHandler(delegateConnection, capturedSql));
        }
    }

    private static final class SqlCapturingConnectionHandler implements InvocationHandler {

        private final Connection delegate;
        private final List<String> capturedSql;

        private SqlCapturingConnectionHandler(Connection delegate, List<String> capturedSql) {
            this.delegate = delegate;
            this.capturedSql = capturedSql;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if (("prepareStatement".equals(methodName) || "prepareCall".equals(methodName))
                    && args != null
                    && args.length > 0
                    && args[0] instanceof String sqlString) {
                capturedSql.add(sqlString);
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException invocationTargetException) {
                throw invocationTargetException.getCause();
            }
        }
    }
}
