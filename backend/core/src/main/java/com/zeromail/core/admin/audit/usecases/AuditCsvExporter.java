package com.zeromail.core.admin.audit.usecases;

import com.zeromail.core.admin.audit.projection.AdminAuditPageQuery;
import com.zeromail.core.admin.audit.projection.AdminAuditRow;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AuditCsvExporter {

    private static final String HEADER =
            "audit_id,actor_email,action,target_kind,target_id,reason,request_ip,created_at_iso";

    private final AdminAuditQueryService adminAuditQueryService;

    public AuditCsvExporter(AdminAuditQueryService adminAuditQueryService) {
        this.adminAuditQueryService =
                Objects.requireNonNull(
                        adminAuditQueryService, "adminAuditQueryService must not be null");
    }

    public void streamCsv(AdminAuditPageQuery query, OutputStream outputStream) {
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        try {
            OutputStreamWriter outputStreamWriter =
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            outputStreamWriter.write(HEADER);
            outputStreamWriter.write("\n");
            for (AdminAuditRow auditRow : adminAuditQueryService.page(query).items()) {
                outputStreamWriter.write(csvRow(auditRow));
                outputStreamWriter.write("\n");
            }
            outputStreamWriter.flush();
        } catch (IOException ioException) {
            throw new UncheckedIOException("Unable to stream admin audit CSV", ioException);
        }
    }

    private static String csvRow(AdminAuditRow auditRow) {
        return String.join(
                ",",
                escape(auditRow.auditId().toString()),
                escape(auditRow.actorEmail()),
                escape(auditRow.action()),
                escape(auditRow.targetKind()),
                escape(auditRow.targetId() == null ? null : auditRow.targetId().toString()),
                escape(auditRow.reason()),
                escape(auditRow.requestIp()),
                escape(auditRow.createdAt().toString()));
    }

    private static String escape(String value) {
        String normalizedValue = value == null ? "" : value;
        boolean requiresQuoting =
                normalizedValue.indexOf(',') >= 0
                        || normalizedValue.indexOf('"') >= 0
                        || normalizedValue.indexOf('\n') >= 0
                        || normalizedValue.indexOf('\r') >= 0;
        if (!requiresQuoting) {
            return normalizedValue;
        }
        return "\"" + normalizedValue.replace("\"", "\"\"") + "\"";
    }
}
