package org.middleware.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExcelMarkdownExtractor {

    private static final int MAX_SHEETS = 5;
    private static final int MAX_ROWS_PER_SHEET = 200;
    private static final int MAX_COLUMNS_PER_SHEET = 40;

    public ExcelExtractionResult extract(Path filePath, String fileName) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder markdown = new StringBuilder();
            int totalRows = 0;
            int sheetsRead = Math.min(workbook.getNumberOfSheets(), MAX_SHEETS);

            markdown.append("# Fichier Excel importe\n\n");
            markdown.append("- Nom: ").append(safeText(fileName)).append('\n');
            markdown.append("- Format: .xlsx\n\n");

            for (int sheetIndex = 0; sheetIndex < sheetsRead; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                int rowsRead = appendSheet(markdown, sheet, formatter);
                totalRows += rowsRead;
            }

            if (workbook.getNumberOfSheets() > MAX_SHEETS) {
                markdown.append("\n> Extraction limitee aux ")
                    .append(MAX_SHEETS)
                    .append(" premieres feuilles.\n");
            }

            return new ExcelExtractionResult(
                fileName,
                workbook.getNumberOfSheets(),
                sheetsRead,
                totalRows,
                markdown.toString().trim()
            );
        }
    }

    private int appendSheet(StringBuilder markdown, Sheet sheet, DataFormatter formatter) {
        int firstRowNum = sheet.getFirstRowNum();
        int lastRowNum = sheet.getLastRowNum();
        int maxColumnCount = findColumnCount(sheet, firstRowNum, lastRowNum);

        markdown.append("## Feuille: ")
            .append(safeText(sheet.getSheetName()))
            .append("\n\n");

        if (maxColumnCount == 0) {
            markdown.append("_Feuille vide._\n\n");
            return 0;
        }

        appendHeader(markdown, maxColumnCount);
        appendSeparator(markdown, maxColumnCount);

        int rowsRead = 0;
        for (int rowIndex = firstRowNum; rowIndex <= lastRowNum && rowsRead < MAX_ROWS_PER_SHEET; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row, formatter, maxColumnCount)) {
                continue;
            }
            appendRow(markdown, row, formatter, maxColumnCount);
            rowsRead++;
        }

        if (lastRowNum - firstRowNum + 1 > MAX_ROWS_PER_SHEET) {
            markdown.append("\n> Feuille limitee aux ")
                .append(MAX_ROWS_PER_SHEET)
                .append(" premieres lignes non vides.\n");
        }
        markdown.append('\n');
        return rowsRead;
    }

    private int findColumnCount(Sheet sheet, int firstRowNum, int lastRowNum) {
        int maxColumnCount = 0;
        for (int rowIndex = firstRowNum; rowIndex <= lastRowNum; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && row.getLastCellNum() > maxColumnCount) {
                maxColumnCount = row.getLastCellNum();
            }
        }
        return Math.min(maxColumnCount, MAX_COLUMNS_PER_SHEET);
    }

    private void appendHeader(StringBuilder markdown, int columnCount) {
        markdown.append('|');
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            markdown.append(' ').append(columnName(columnIndex)).append(" |");
        }
        markdown.append('\n');
    }

    private void appendSeparator(StringBuilder markdown, int columnCount) {
        markdown.append('|');
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            markdown.append(" --- |");
        }
        markdown.append('\n');
    }

    private void appendRow(StringBuilder markdown, Row row, DataFormatter formatter, int columnCount) {
        markdown.append('|');
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            String value = formatter.formatCellValue(row.getCell(columnIndex));
            markdown.append(' ').append(escapeMarkdownCell(value)).append(" |");
        }
        markdown.append('\n');
    }

    private boolean isBlankRow(Row row, DataFormatter formatter, int columnCount) {
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            if (!formatter.formatCellValue(row.getCell(columnIndex)).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index;
        do {
            name.insert(0, (char) ('A' + (value % 26)));
            value = value / 26 - 1;
        } while (value >= 0);
        return name.toString();
    }

    private String escapeMarkdownCell(String value) {
        return safeText(value)
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    public record ExcelExtractionResult(
        String fileName,
        int sheetCount,
        int sheetsRead,
        int rowsRead,
        String markdown
    ) {
    }
}
