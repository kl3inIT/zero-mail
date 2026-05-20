package com.zeromail.core.admin.spend.usecases;

import com.zeromail.core.admin.spend.exception.SpendExportTooLargeException;
import com.zeromail.core.admin.spend.persistence.lowlevel.SpendAggregateReadRepository;
import com.zeromail.core.admin.spend.projection.SpendCsvRow;
import com.zeromail.core.admin.spend.projection.SpendQuery;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV exporter for the /admin/spend dashboard. Per R-8F-H5, a pre-query row-count estimate runs
 * before any bytes are written; if the estimate exceeds {@link #MAX_ROWS}, the export is rejected
 * with {@link SpendExportTooLargeException} BEFORE the response body starts.
 *
 * <p>SQL lives in {@link SpendAggregateReadRepository}; this service handles only CSV escaping +
 * writer plumbing.
 *
 * <p>Allowed columns (T-08-56): bucketDate, provider, feature, credentialSource, totalCost,
 * callCount. NEVER includes tenant_id, prompt text, completion text, or any content-shaped field.
 */
@Service
public class SpendCsvExporter {

    /** Maximum rows the exporter will stream — protects against a 90-day full-table dump. */
    public static final int MAX_ROWS = 10_000;

    private static final String[] HEADER_COLUMNS = {
        "bucketDate", "provider", "feature", "credentialSource", "totalCost", "callCount"
    };

    private final SpendAggregateReadRepository spendAggregateReadRepository;

    public SpendCsvExporter(SpendAggregateReadRepository spendAggregateReadRepository) {
        this.spendAggregateReadRepository =
                Objects.requireNonNull(
                        spendAggregateReadRepository,
                        "spendAggregateReadRepository must not be null");
    }

    @Transactional(readOnly = true)
    public int estimateRowCount(SpendQuery spendQuery) {
        Objects.requireNonNull(spendQuery, "spendQuery must not be null");
        return spendAggregateReadRepository.estimateCsvGroupCount(spendQuery);
    }

    @Transactional(readOnly = true)
    public void streamCsv(SpendQuery spendQuery, OutputStream outputStream) throws IOException {
        Objects.requireNonNull(spendQuery, "spendQuery must not be null");
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        int estimate = estimateRowCount(spendQuery);
        if (estimate > MAX_ROWS) {
            throw new SpendExportTooLargeException(estimate, MAX_ROWS);
        }

        List<SpendCsvRow> rows = spendAggregateReadRepository.findCsvRows(spendQuery, MAX_ROWS);

        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writeRow(writer, HEADER_COLUMNS);
        for (SpendCsvRow row : rows) {
            BigDecimal totalCost = row.totalCost().setScale(6, RoundingMode.HALF_UP);
            writeRow(
                    writer,
                    row.bucketDate().toString(),
                    row.provider(),
                    row.feature(),
                    row.credentialSource(),
                    totalCost.toPlainString(),
                    Integer.toString(row.callCount()));
        }
        writer.flush();
    }

    private static void writeRow(Writer writer, String... columns) throws IOException {
        for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
            if (columnIndex > 0) {
                writer.write(',');
            }
            writer.write(escape(columns[columnIndex]));
        }
        writer.write('\n');
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        // WR-05: prefix cells that start with =/+/-/@/\t/\r with a leading single quote so
        // Excel/LibreOffice/Numbers do not interpret them as formulas. Current columns are
        // all enum/numeric so this is forward-looking, but the cost is trivial and the
        // exploit (e.g. =HYPERLINK("…")) is well known.
        String cell = value;
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        if (cell.indexOf(',') >= 0
                || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0
                || cell.indexOf('\r') >= 0) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }
}
