package com.abyss.toolkit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

public class ReportService {

    public record ReportData(String target, String reportType, Map<String, Object> results, String summary) {}

    public static Path generatePdf(ReportData data, Path outputDir) throws IOException {
        Path file = outputDir.resolve(fileName(data, "pdf"));
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - 60;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                cs.beginText(); cs.newLineAtOffset(50, y); cs.showText("Abyss Report"); cs.endText(); y -= 30;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                y = writeLine(cs, "Target: " + data.target(), 50, y);
                y = writeLine(cs, "Type: " + data.reportType(), 50, y);
                y -= 10;
                for (var entry : data.results().entrySet()) {
                    if (y < 60) break;
                    y = writeLine(cs, " - " + entry.getKey() + ": " + entry.getValue(), 60, y);
                }
                y -= 10;
                writeLine(cs, "Summary: " + data.summary(), 50, y);
            }
            doc.save(file.toFile());
        }
        return file;
    }
    private static float writeLine(PDPageContentStream cs, String text, float x, float y) throws IOException {
        cs.beginText(); cs.newLineAtOffset(x, y); cs.showText(truncate(text, 120)); cs.endText(); return y - 16;
    }
    private static String truncate(String s, int max) { return s == null ? "" : (s.length() > max ? s.substring(0, max-3)+"..." : s); }

    public static Path generateHtml(ReportData data, Path outputDir) throws IOException {
        Path file = outputDir.resolve(fileName(data, "html"));
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>Abyss Report</title>")
                .append("<style>body{background:#0D0D0D;color:#E0E0E0;font-family:sans-serif;padding:30px;max-width:1200px;margin:auto}")
                .append("h1{color:#C8A86E;border-bottom:2px solid #C8A86E;padding-bottom:10px}")
                .append(".card{background:#181818;border-radius:8px;padding:16px;margin:10px 0;border:1px solid #2A2A2A}")
                .append(".key{color:#888}.value{color:#8BC34A;font-family:monospace}</style></head><body>")
                .append("<h1>Abyss Report</h1><div class='card'><b>Target:</b> ").append(escape(data.target())).append("<br>")
                .append("<b>Generated:</b> ").append(LocalDateTime.now()).append("</div><div class='card'>");
        for (var entry : data.results().entrySet()) {
            html.append("<div><span class='key'>").append(escape(entry.getKey())).append(":</span> <span class='value'>")
                    .append(escape(String.valueOf(entry.getValue()))).append("</span></div>");
        }
        html.append("</div><div class='card'><b>Summary:</b> ").append(escape(data.summary())).append("</div></body></html>");
        Files.writeString(file, html.toString(), StandardCharsets.UTF_8);
        return file;
    }

    public static Path generateJson(ReportData data, Path outputDir) throws IOException {
        Path file = outputDir.resolve(fileName(data, "json"));
        Map<String, Object> root = Map.of("timestamp", LocalDateTime.now().toString(), "target", data.target(),
                "reportType", data.reportType(), "results", data.results(), "summary", data.summary());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
        return file;
    }

    public static Path generateExcel(ReportData data, Path outputDir) throws IOException {
        Path file = outputDir.resolve(fileName(data, "xlsx"));
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Report");
            int rowNum = 0;
            Row r = sheet.createRow(rowNum++); r.createCell(0).setCellValue("Key"); r.createCell(1).setCellValue("Value");
            r = sheet.createRow(rowNum++); r.createCell(0).setCellValue("Target"); r.createCell(1).setCellValue(data.target());
            r = sheet.createRow(rowNum++); r.createCell(0).setCellValue("Type"); r.createCell(1).setCellValue(data.reportType());
            r = sheet.createRow(rowNum++); r.createCell(0).setCellValue("Timestamp"); r.createCell(1).setCellValue(LocalDateTime.now().toString());
            rowNum++;
            for (var entry : data.results().entrySet()) {
                r = sheet.createRow(rowNum++);
                r.createCell(0).setCellValue(entry.getKey());
                r.createCell(1).setCellValue(String.valueOf(entry.getValue()));
            }
            rowNum++;
            r = sheet.createRow(rowNum); r.createCell(0).setCellValue("Summary"); r.createCell(1).setCellValue(data.summary());
            sheet.autoSizeColumn(0); sheet.autoSizeColumn(1);
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) { wb.write(fos); }
        }
        return file;
    }

    public static Path generateCsv(ReportData data, Path outputDir) throws IOException {
        Path file = outputDir.resolve(fileName(data, "csv"));
        StringBuilder csv = new StringBuilder();
        csv.append("Key,Value\nTarget,").append(escapeCsv(data.target())).append("\n");
        csv.append("Type,").append(escapeCsv(data.reportType())).append("\n");
        csv.append("Timestamp,").append(escapeCsv(LocalDateTime.now().toString())).append("\n\n");
        for (var entry : data.results().entrySet()) {
            csv.append(escapeCsv(entry.getKey())).append(",").append(escapeCsv(String.valueOf(entry.getValue()))).append("\n");
        }
        csv.append("\nSummary,").append(escapeCsv(data.summary())).append("\n");
        Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);
        return file;
    }

    public static Path generateMarkdown(ReportData data, Path outputDir) throws IOException {
        Path file = outputDir.resolve(fileName(data, "md"));
        StringBuilder md = new StringBuilder();
        md.append("# Abyss Report\n\n**Target:** ").append(data.target()).append("\n\n");
        md.append("**Generated:** ").append(LocalDateTime.now()).append("\n\n## Findings\n\n");
        for (var entry : data.results().entrySet()) {
            md.append("- **").append(entry.getKey()).append(":** ").append(entry.getValue()).append("\n");
        }
        md.append("\n## Summary\n\n").append(data.summary()).append("\n");
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private static String fileName(ReportData data, String ext) {
        return "abyss_" + data.target().replaceAll("[^a-zA-Z0-9.-]", "_") + "_" + System.currentTimeMillis() + "." + ext;
    }
    private static String escape(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}