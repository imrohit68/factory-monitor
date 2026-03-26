package com.example.system.service;

import com.example.system.config.AppProperties;
import com.example.system.domain.EventRecord;
import com.example.system.repository.EventLogRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventExportService {

    private final EventLogRepository events;
    private final AppProperties app;

    /**
     * Writes all {@code event_log} rows with {@code event_time} in the inclusive local date range
     * {@code [start, end]}, as UTF-8 CSV (with BOM for Excel). Append-only: both OPEN and CLOSED rows
     * appear. {@code event_time} is the UTC {@link Instant} as ISO-8601, truncated to whole seconds (no
     * fractional part).
     * <p>
     * Sets HTTP headers, filename, BOM, and body. Call after binding dates; propagates validation errors
     * from {@link #validateRange}.
     */
    public void writeCsvAttachment(LocalDate start, LocalDate end, HttpServletResponse response) throws IOException {
        validateRange(start, end);
        String filename = String.format("event-log-%s-to-%s.csv", start, end);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        writeCsvContent(start, end, response.getOutputStream());
        response.flushBuffer();
    }

    /**
     * Writes CSV bytes to {@code out} for {@code [start, end]} (inclusive local dates). Validates range first.
     */
    public void writeCsv(LocalDate start, LocalDate end, OutputStream out) throws IOException {
        validateRange(start, end);
        writeCsvContent(start, end, out);
    }

    private void writeCsvContent(LocalDate start, LocalDate end, OutputStream out) throws IOException {
        ZoneId z = ZoneId.systemDefault();
        Instant from = start.atStartOfDay(z).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(z).toInstant();
        List<EventRecord> rows =
                events.findByEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(from, to);

        try (OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write('\uFEFF');
            w.write("id,mapping_id,input_bit_index,output_slave_id,output_channel,status,event_time\n");
            for (EventRecord e : rows) {
                w.write(Long.toString(e.getId()));
                w.write(',');
                w.write(e.getMappingId() == null ? "" : Long.toString(e.getMappingId()));
                w.write(',');
                w.write(Integer.toString(e.getInputBitIndex()));
                w.write(',');
                w.write(Integer.toString(e.getOutputSlaveId()));
                w.write(',');
                w.write(Integer.toString(e.getOutputChannel()));
                w.write(',');
                w.write(escapeCsv(e.getStatus() == null ? "" : e.getStatus().name()));
                w.write(',');
                w.write(escapeCsv(formatUtcInstantToSeconds(e.getEventTime())));
                w.write('\n');
            }
        }
    }

    public void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end dates are required.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End date must be on or after the start date.");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(start, end) + 1;
        int max = app.getLogRetentionDays();
        if (inclusiveDays > max) {
            throw new IllegalArgumentException("Date range cannot exceed " + max + " days (inclusive).");
        }
    }

    /** ISO-8601 UTC instant, second precision only (e.g. {@code 2026-03-22T01:54:27Z}). */
    static String formatUtcInstantToSeconds(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    static String escapeCsv(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.indexOf(',') < 0 && raw.indexOf('"') < 0 && raw.indexOf('\n') < 0 && raw.indexOf('\r') < 0) {
            return raw;
        }
        return '"' + raw.replace("\"", "\"\"") + '"';
    }
}
