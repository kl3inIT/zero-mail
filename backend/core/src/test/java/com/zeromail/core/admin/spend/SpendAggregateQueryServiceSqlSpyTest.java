package com.zeromail.core.admin.spend;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.spend.persistence.lowlevel.SpendAggregateReadRepository;
import com.zeromail.core.admin.spend.projection.SpendDashboardSnapshot;
import com.zeromail.core.admin.spend.projection.SpendQuery;
import com.zeromail.core.admin.spend.usecases.SpendAggregateQueryService;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.support.PostgresContainerTest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * R-8F-H4 + ARCH-11 runtime gate. Spies on every SQL string emitted during {@link
 * SpendAggregateQueryService#snapshot(SpendQuery)} and asserts NONE references any of {@code
 * prompt}, {@code completion}, {@code request_body}, {@code response_body}, or content-shaped
 * column literals.
 *
 * <p>This is the runtime contract complementing the static {@link
 * com.zeromail.core.admin.arch.AdminSpendPromptAccessorBanTest} ArchUnit gate.
 */
class SpendAggregateQueryServiceSqlSpyTest extends PostgresContainerTest {

    @Autowired private DataSource dataSource;
    @Autowired private ZeroMailCoreProperties coreProperties;

    @Test
    void snapshot_never_selects_prompt_or_completion_columns() {
        List<String> capturedSql = Collections.synchronizedList(new ArrayList<>());
        DataSource spyingDataSource = new SqlCapturingDataSource(dataSource, capturedSql);
        SpendAggregateReadRepository spendAggregateReadRepository =
                new SpendAggregateReadRepository(new NamedParameterJdbcTemplate(spyingDataSource));
        SpendAggregateQueryService spendAggregateQueryService =
                new SpendAggregateQueryService(
                        spendAggregateReadRepository, Clock.systemUTC(), coreProperties);

        Instant now = Instant.now();
        SpendQuery spendQuery =
                new SpendQuery(
                        now.minus(Duration.ofDays(7)),
                        now.plus(Duration.ofMinutes(1)),
                        Optional.empty(),
                        Optional.empty());
        SpendDashboardSnapshot snapshot = spendAggregateQueryService.snapshot(spendQuery);

        assertThat(snapshot).isNotNull();
        assertThat(capturedSql).as("at least one SQL statement was emitted").isNotEmpty();
        for (String emittedSql : capturedSql) {
            String lowered = emittedSql.toLowerCase();
            assertThat(lowered)
                    .as("emitted SQL must not reference prompt/completion text: %s", emittedSql)
                    .doesNotContain("prompt_text")
                    .doesNotContain("completion_text")
                    .doesNotContain("request_body")
                    .doesNotContain("response_body")
                    .doesNotContain("body_text")
                    .doesNotContain("content_text");
        }
    }

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
