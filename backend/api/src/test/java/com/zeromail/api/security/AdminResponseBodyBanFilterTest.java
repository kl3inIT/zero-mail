package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminResponseBodyBanFilterTest {

    @Test
    void admin_response_with_long_content_field_is_replaced_and_audited() throws Exception {
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        UUID auditId = UUID.fromString("00000000-0000-4000-8000-000000000811");
        when(adminAuditWriter.appendAsSystem(
                        eq(AdminAuditAction.ADMIN_RESPONSE_BODY_BAN_TRIPPED),
                        eq("ADMIN_RESPONSE"),
                        isNull(),
                        isNull(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(auditId);
        AdminResponseBodyBanFilter filter = new AdminResponseBodyBanFilter(adminAuditWriter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/test-leak");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain =
                (_, servletResponse) -> {
                    servletResponse.setContentType("application/json");
                    servletResponse
                            .getWriter()
                            .write("{\"postBodyText\":\"" + "x".repeat(250) + "\"}");
                };

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("error.admin.body_ban");
        assertThat(response.getContentAsString()).contains(auditId.toString());
        verify(adminAuditWriter)
                .appendAsSystem(
                        eq(AdminAuditAction.ADMIN_RESPONSE_BODY_BAN_TRIPPED),
                        eq("ADMIN_RESPONSE"),
                        isNull(),
                        isNull(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    void admin_response_without_forbidden_field_name_passes_through() throws Exception {
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        AdminResponseBodyBanFilter filter = new AdminResponseBodyBanFilter(adminAuditWriter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/metadata");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain =
                (_, servletResponse) -> {
                    servletResponse.setContentType("application/json");
                    servletResponse.getWriter().write("{\"summary\":\"" + "x".repeat(250) + "\"}");
                };

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"summary\"");
        verifyNoInteractions(adminAuditWriter);
    }
}
