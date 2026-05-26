package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.zeromail.core.support.PostgresContainerTest;
import jakarta.persistence.EntityManager;
import java.net.InetAddress;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

class UserByokKeySingleRowPerTenantTest extends PostgresContainerTest {

    @Autowired UserByokService userByokService;

    @Autowired UserByokKeyRepository userByokKeyRepository;

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired EntityManager entityManager;

    @MockitoBean HostResolver hostResolver;

    @Test
    @Transactional
    void savingNewProviderKeepsExactlyOneRowPerTenant() throws Exception {
        when(hostResolver.resolve(anyString()))
                .thenReturn(new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000009422");
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?) on conflict (id) do nothing",
                tenantId,
                "byok-single-row-test");

        userByokService.save(
                tenantId,
                new ByokSaveCommand("OPENAI", "https://api.openai.com/v1", "sk-first-key"));
        userByokService.save(
                tenantId,
                new ByokSaveCommand("ANTHROPIC", "https://api.anthropic.com/v1", "sk-second-key"));
        entityManager.flush();
        entityManager.clear();

        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from user_byok_key where tenant_id = ?",
                        Integer.class,
                        tenantId);
        UserByokKeyEntity reloadedKey =
                userByokKeyRepository.findByTenantId(tenantId).orElseThrow();
        assertThat(rowCount).isEqualTo(1);
        assertThat(reloadedKey.getProviderId()).isEqualTo("ANTHROPIC");
        assertThat(reloadedKey.getBaseUrl()).isEqualTo("https://api.anthropic.com/v1");
    }
}
