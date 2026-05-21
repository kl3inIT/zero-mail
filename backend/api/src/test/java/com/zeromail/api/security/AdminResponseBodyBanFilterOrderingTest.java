package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminResponseBodyBanFilterOrderingTest {

    @Test
    void filter_scans_serialized_json_body_before_copying_to_client() throws Exception {
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        when(adminAuditWriter.appendAsSystem(
                        eq(AdminAuditAction.ADMIN_RESPONSE_BODY_BAN_TRIPPED),
                        eq("ADMIN_RESPONSE"),
                        isNull(),
                        isNull(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(UUID.fromString("00000000-0000-4000-8000-000000000812"));
        AdminResponseBodyBanFilter filter = new AdminResponseBodyBanFilter(adminAuditWriter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/compressed");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain =
                (_, servletResponse) -> {
                    HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
                    servletResponse.setContentType("application/json;charset=UTF-8");
                    httpServletResponse.setHeader("Content-Encoding", "identity");
                    servletResponse
                            .getOutputStream()
                            .write(("{\"content\":\"" + "x".repeat(250) + "\"}").getBytes());
                };

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("error.admin.body_ban");
    }
}
