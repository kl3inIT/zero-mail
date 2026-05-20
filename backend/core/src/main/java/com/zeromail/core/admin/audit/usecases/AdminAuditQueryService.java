package com.zeromail.core.admin.audit.usecases;

import com.zeromail.core.admin.audit.persistence.lowlevel.AdminAuditEventReadRepository;
import com.zeromail.core.admin.audit.projection.AdminAuditPage;
import com.zeromail.core.admin.audit.projection.AdminAuditPageQuery;
import com.zeromail.core.admin.audit.projection.AdminAuditRow;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditQueryService {

    private final AdminAuditEventReadRepository adminAuditEventReadRepository;

    public AdminAuditQueryService(AdminAuditEventReadRepository adminAuditEventReadRepository) {
        this.adminAuditEventReadRepository =
                Objects.requireNonNull(
                        adminAuditEventReadRepository,
                        "adminAuditEventReadRepository must not be null");
    }

    @Transactional(readOnly = true)
    public AdminAuditPage page(AdminAuditPageQuery query) {
        AdminAuditPageQuery pageQuery = Objects.requireNonNull(query, "query must not be null");
        List<AdminAuditRow> rows = adminAuditEventReadRepository.findPage(pageQuery);
        boolean hasNextPage = rows.size() > pageQuery.limit();
        List<AdminAuditRow> pageItems = hasNextPage ? rows.subList(0, pageQuery.limit()) : rows;
        return new AdminAuditPage(pageItems, hasNextPage);
    }
}
