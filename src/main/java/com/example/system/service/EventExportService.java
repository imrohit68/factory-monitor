package com.example.system.service;

import com.example.system.config.ApplicationOperationMode;
import com.example.system.config.AppProperties;
import com.example.system.config.OperationMode;
import com.example.system.domain.EventRecord;
import com.example.system.domain.Workstation;
import com.example.system.domain.WorkstationSlot;
import com.example.system.repository.EventLogRepository;
import com.example.system.repository.WorkstationRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventExportService {

    private static final Color HEADER_BG = new Color(220, 220, 220);
    private static final Color ROW_ALT = new Color(248, 248, 248);
    private static final Locale PDF_LOCALE = Locale.ENGLISH;
    private static final DateTimeFormatter EXPORT_LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final EventLogRepository events;
    private final WorkstationRepository workstations;
    private final AppProperties app;
    private final ApplicationOperationMode applicationOperationMode;

    /**
     * Writes {@code event_log} rows for the {@linkplain ApplicationOperationMode#getCurrentMode() current
     * operation mode} with non-null {@code mode}, whose {@code event_time} falls in the inclusive local date
     * range {@code [start, end]}, as UTF-8 CSV (with BOM for Excel). A title line names the mode; append-only:
     * both OPEN and CLOSED rows appear. The last two columns are {@code event_date} ({@code yyyy-MM-dd}) and
     * {@code event_time} ({@code HH:mm:ss}) in the JVM default time zone, second precision only (no fractional
     * seconds).
     * <p>
     * Sets HTTP headers, filename, BOM, and body. Call after binding dates; propagates validation errors
     * from {@link #validateRange}.
     */
    public void writeCsvAttachment(LocalDate start, LocalDate end, HttpServletResponse response) throws IOException {
        List<EventRecord> rows = loadEventsInRange(start, end);
        String filename = exportFilenameBase(start, end, "csv");
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        writeCsvBody(rows, response.getOutputStream());
        response.flushBuffer();
    }

    /**
     * Writes CSV bytes to {@code out} for {@code [start, end]} (inclusive local dates). Same shape as
     * {@link #writeCsvAttachment}; {@code event_date} / {@code event_time} use {@link ZoneId#systemDefault()}.
     */
    public void writeCsv(LocalDate start, LocalDate end, OutputStream out) throws IOException {
        List<EventRecord> rows = loadEventsInRange(start, end);
        writeCsvBody(rows, out);
    }

    /**
     * Writes event rows in {@code [start, end]} for the current operation mode (non-null {@code mode} only)
     * as a PDF table. Same data window and event date/time columns as CSV ({@code event_date} /
     * {@code event_time} in {@link ZoneId#systemDefault()}, second precision).
     */
    public void writePdfAttachment(LocalDate start, LocalDate end, HttpServletResponse response) throws IOException {
        List<EventRecord> rows = loadEventsInRange(start, end);
        String filename = exportFilenameBase(start, end, "pdf");
        response.setContentType("application/pdf");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        writePdfBody(rows, start, end, response.getOutputStream());
        response.flushBuffer();
    }

    private List<EventRecord> loadEventsInRange(LocalDate start, LocalDate end) {
        validateRange(start, end);
        ZoneId z = ZoneId.systemDefault();
        Instant from = start.atStartOfDay(z).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(z).toInstant();
        OperationMode mode = applicationOperationMode.getCurrentMode();
        return events.findByEventTimeRangeAndModeOrderByEventTimeAsc(from, to, mode);
    }

    private String exportFilenameBase(LocalDate start, LocalDate end, String extension) {
        OperationMode mode = applicationOperationMode.getCurrentMode();
        return String.format(
                "event-log-%s-%s-to-%s.%s",
                mode.name().toLowerCase(Locale.ROOT), start, end, extension);
    }

    private String exportTitleLine() {
        return applicationOperationMode.getCurrentMode().getDisplayName() + " — Event log";
    }

    private void writeCsvBody(List<EventRecord> rows, OutputStream out) throws IOException {
        ZoneId zone = ZoneId.systemDefault();
        Map<Long, String> workStationBySlotId = resolveWorkStationLabelsBySlotId();
        try (OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write('\uFEFF');
            w.write(exportTitleLine());
            w.write('\n');
            w.write('\n');
            w.write("id,work_station,input_bit_index,output_slave_id,output_channel,status,event_date,event_time\n");
            for (EventRecord e : rows) {
                w.write(Long.toString(e.getId()));
                w.write(',');
                w.write(escapeCsv(workStationLabelFor(e.getMappingId(), workStationBySlotId)));
                w.write(',');
                w.write(Integer.toString(e.getInputBitIndex()));
                w.write(',');
                w.write(Integer.toString(e.getOutputSlaveId()));
                w.write(',');
                w.write(Integer.toString(e.getOutputChannel()));
                w.write(',');
                w.write(escapeCsv(e.getStatus() == null ? "" : e.getStatus().name()));
                w.write(',');
                appendCsvEventDateTime(e.getEventTime(), zone, w);
                w.write('\n');
            }
        }
    }

    private static void appendCsvEventDateTime(Instant instant, ZoneId zone, OutputStreamWriter w)
            throws IOException {
        if (instant == null) {
            w.write(',');
            return;
        }
        w.write(escapeCsv(formatExportEventDate(instant, zone)));
        w.write(',');
        w.write(escapeCsv(formatExportEventTime(instant, zone)));
    }

    /** {@code yyyy-MM-dd} in {@code zone}, or empty if {@code instant} is null. */
    static String formatExportEventDate(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "";
        }
        ZonedDateTime zdt = instant.truncatedTo(ChronoUnit.SECONDS).atZone(zone);
        return zdt.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /** {@code HH:mm:ss} in {@code zone}, or empty if {@code instant} is null. */
    static String formatExportEventTime(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "";
        }
        ZonedDateTime zdt = instant.truncatedTo(ChronoUnit.SECONDS).atZone(zone);
        return zdt.format(EXPORT_LOCAL_TIME);
    }

    private void writePdfBody(List<EventRecord> rows, LocalDate start, LocalDate end, OutputStream out)
            throws IOException {
        Document document = new Document(PageSize.A4.rotate());
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f);
            document.add(new Paragraph(exportTitleLine(), titleFont));
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10f);
            Font periodLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f);
            Phrase periodLine = new Phrase();
            periodLine.add(new Chunk("Period: ", periodLabelFont));
            periodLine.add(new Chunk(formatPdfPeriodLine(start, end), subFont));
            document.add(new Paragraph(periodLine));
            document.add(new Paragraph(" ", subFont));

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f);

            ZoneId zone = ZoneId.systemDefault();
            Map<Long, String> workStationBySlotId = resolveWorkStationLabelsBySlotId();
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[] {0.65f, 1.0f, 0.85f, 1.0f, 1.0f, 0.85f, 1.15f, 1.7f});
            table.setSpacingBefore(4f);

            String[] headers = {
                "ID",
                "Work Station",
                "Input bit",
                "Output slave",
                "Output channel",
                "Status",
                "Event date",
                "Event time"
            };
            for (String h : headers) {
                table.addCell(headerCell(h, headerFont));
            }
            table.setHeaderRows(1);

            int i = 0;
            for (EventRecord e : rows) {
                Color bg = (i % 2 == 0) ? Color.WHITE : ROW_ALT;
                table.addCell(dataCell(Long.toString(e.getId()), cellFont, bg));
                table.addCell(
                        dataCell(workStationLabelFor(e.getMappingId(), workStationBySlotId), cellFont, bg));
                table.addCell(dataCell(Integer.toString(e.getInputBitIndex()), cellFont, bg));
                table.addCell(dataCell(Integer.toString(e.getOutputSlaveId()), cellFont, bg));
                table.addCell(dataCell(Integer.toString(e.getOutputChannel()), cellFont, bg));
                table.addCell(
                        dataCell(e.getStatus() == null ? "" : e.getStatus().name(), cellFont, bg));
                table.addCell(dataCell(formatExportEventDate(e.getEventTime(), zone), cellFont, bg));
                table.addCell(dataCell(formatExportEventTime(e.getEventTime(), zone), cellFont, bg));
                i++;
            }

            document.add(table);
        } catch (DocumentException e) {
            throw new IOException("Failed to build PDF", e);
        } finally {
            document.close();
        }
    }

    private static String formatPdfPeriodLine(LocalDate start, LocalDate end) {
        DateTimeFormatter df = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(PDF_LOCALE);
        return start.format(df) + " – " + end.format(df);
    }

    /**
     * Resolves each workstation slot id ({@code event_log.mapping_id}) to the export Work Station label
     * derived from the parent workstation name ({@code BC}/{@code FC} prefix).
     */
    private Map<Long, String> resolveWorkStationLabelsBySlotId() {
        Map<Long, String> bySlotId = new HashMap<>();
        for (Workstation w : workstations.findAllByOrderBySortOrderAscIdAsc()) {
            String label = formatWorkstationExportName(w.getName());
            for (WorkstationSlot slot : w.getSlots()) {
                bySlotId.put(slot.getId(), label);
            }
        }
        return bySlotId;
    }

    private static String workStationLabelFor(Long mappingId, Map<Long, String> workStationBySlotId) {
        if (mappingId == null) {
            return "";
        }
        return workStationBySlotId.getOrDefault(mappingId, "");
    }

    /**
     * Work Station export label: {@code FC21}/{@code FC22} for names 21 and 22; otherwise {@code BC}
     * plus the workstation name (e.g. {@code BC1}).
     */
    static String formatWorkstationExportName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if ("21".equals(trimmed) || "22".equals(trimmed)) {
            return "FC" + trimmed;
        }
        return "BC" + trimmed;
    }

    private static PdfPCell headerCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(HEADER_BG);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(6f);
        c.setBorderWidth(0.5f);
        return c;
    }

    private static PdfPCell dataCell(String text, Font font, Color background) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setBackgroundColor(background);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4f);
        c.setBorderWidth(0.5f);
        return c;
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
