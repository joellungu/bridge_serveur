package org.middleware.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.middleware.dto.ExcelInvoiceRow;
import org.middleware.models.Entreprise;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Service pour traiter les fichiers Excel de factures.
 * - Lit le fichier Excel uploadé
 * - Normalise et valide les données
 * - Ajoute les informations de l'entreprise
 * - Retourne un fichier Excel enrichi
 */
@ApplicationScoped
public class ExcelInvoiceService {

    private static final Logger LOG = Logger.getLogger(ExcelInvoiceService.class.getName());

    // Colonnes d'entrée (fournies par l'utilisateur)
    private static final String[] INPUT_HEADERS = {
        "rn", "type", "clientNif", "clientName", 
        "itemCode", "itemName", "itemPrice", "itemQuantity", 
        "itemTaxGroup", "currency", "mode"
    };

    // Colonnes de sortie (ajoutées par le système)
    private static final String[] OUTPUT_HEADERS = {
        "rn", "type", "clientNif", "clientName", 
        "itemCode", "itemName", "itemPrice", "itemQuantity", 
        "itemTaxGroup", "currency", "mode",
        "nif", "isf", "companyName", "subtotal", "total", "status", "errorMessage"
    };

    /**
     * Traite un fichier Excel et retourne un fichier enrichi
     * 
     * @param inputStream Le fichier Excel uploadé
     * @param entreprise L'entreprise de l'utilisateur connecté
     * @return byte[] Le fichier Excel enrichi
     */
    public byte[] processExcelFile(InputStream inputStream, Entreprise entreprise) throws IOException {
        LOG.info("=== Début du traitement Excel pour: " + entreprise.nom + " ===");
        
        List<ExcelInvoiceRow> rows = readExcelFile(inputStream);
        LOG.info("Lignes lues: " + rows.size());
        
        // Enrichir chaque ligne avec les données de l'entreprise
        Set<String> seenRns = new HashSet<>();
        for (ExcelInvoiceRow row : rows) {
            // Ajouter les infos de l'entreprise
            row.nif = entreprise.nif;
            row.isf = entreprise.isf;
            row.companyName = entreprise.nom;
            
            // Normaliser les valeurs
            row.normalize();
            
            // Vérifier les doublons
            if (seenRns.contains(row.rn)) {
                row.status = "DUPLICATE";
                row.errorMessage = "Numéro de facture en double dans le fichier";
            } else {
                seenRns.add(row.rn);
                // Valider la ligne
                row.validate();
            }
            
            // Calculer les totaux si valide
            if ("VALID".equals(row.status)) {
                row.calculateTotals();
            }
        }
        
        // Générer le fichier Excel de sortie
        byte[] outputFile = writeExcelFile(rows);
        LOG.info("=== Traitement Excel terminé. Lignes traitées: " + rows.size() + " ===");
        
        return outputFile;
    }

    /**
     * Lit le fichier Excel et extrait les lignes
     */
    private List<ExcelInvoiceRow> readExcelFile(InputStream inputStream) throws IOException {
        List<ExcelInvoiceRow> rows = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Ignorer la première ligne (en-têtes)
            boolean isFirstRow = true;
            
            for (Row row : sheet) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }
                
                // Ignorer les lignes vides
                if (isRowEmpty(row)) {
                    continue;
                }
                
                ExcelInvoiceRow invoiceRow = parseRow(row);
                rows.add(invoiceRow);
            }
        }
        
        return rows;
    }

    /**
     * Parse une ligne Excel en ExcelInvoiceRow
     */
    private ExcelInvoiceRow parseRow(Row row) {
        ExcelInvoiceRow invoiceRow = new ExcelInvoiceRow();
        
        invoiceRow.rn = getStringValue(row.getCell(0));
        invoiceRow.type = getStringValue(row.getCell(1));
        invoiceRow.clientNif = getStringValue(row.getCell(2));
        invoiceRow.clientName = getStringValue(row.getCell(3));
        invoiceRow.itemCode = getStringValue(row.getCell(4));
        invoiceRow.itemName = getStringValue(row.getCell(5));
        invoiceRow.itemPrice = getBigDecimalValue(row.getCell(6));
        invoiceRow.itemQuantity = getBigDecimalValue(row.getCell(7));
        invoiceRow.itemTaxGroup = getStringValue(row.getCell(8));
        invoiceRow.currency = getStringValue(row.getCell(9));
        invoiceRow.mode = getStringValue(row.getCell(10));
        
        return invoiceRow;
    }

    /**
     * Génère le fichier Excel de sortie avec toutes les colonnes
     */
    private byte[] writeExcelFile(List<ExcelInvoiceRow> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Factures Traitées");
            
            // Créer les styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle validStyle = createValidStyle(workbook);
            CellStyle invalidStyle = createInvalidStyle(workbook);
            CellStyle duplicateStyle = createDuplicateStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            
            // En-têtes
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < OUTPUT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(OUTPUT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Données
            int rowNum = 1;
            for (ExcelInvoiceRow invoiceRow : rows) {
                Row row = sheet.createRow(rowNum++);
                
                // Déterminer le style selon le statut
                CellStyle rowStyle = switch (invoiceRow.status) {
                    case "VALID" -> validStyle;
                    case "DUPLICATE" -> duplicateStyle;
                    default -> invalidStyle;
                };
                
                // Colonnes d'entrée
                createCell(row, 0, invoiceRow.rn, rowStyle);
                createCell(row, 1, invoiceRow.type, rowStyle);
                createCell(row, 2, invoiceRow.clientNif, rowStyle);
                createCell(row, 3, invoiceRow.clientName, rowStyle);
                createCell(row, 4, invoiceRow.itemCode, rowStyle);
                createCell(row, 5, invoiceRow.itemName, rowStyle);
                createNumericCell(row, 6, invoiceRow.itemPrice, numberStyle);
                createNumericCell(row, 7, invoiceRow.itemQuantity, numberStyle);
                createCell(row, 8, invoiceRow.itemTaxGroup, rowStyle);
                createCell(row, 9, invoiceRow.currency, rowStyle);
                createCell(row, 10, invoiceRow.mode, rowStyle);
                
                // Colonnes ajoutées
                createCell(row, 11, invoiceRow.nif, rowStyle);
                createCell(row, 12, invoiceRow.isf, rowStyle);
                createCell(row, 13, invoiceRow.companyName, rowStyle);
                createNumericCell(row, 14, invoiceRow.subtotal, numberStyle);
                createNumericCell(row, 15, invoiceRow.total, numberStyle);
                createCell(row, 16, invoiceRow.status, rowStyle);
                createCell(row, 17, invoiceRow.errorMessage, rowStyle);
            }
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < OUTPUT_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Génère un fichier Excel template vide
     */
    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Template Factures");
            
            // Style pour les en-têtes
            CellStyle headerStyle = createHeaderStyle(workbook);
            
            // En-têtes (colonnes d'entrée uniquement)
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < INPUT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(INPUT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Ligne d'exemple
            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("FAC-2026-001");
            exampleRow.createCell(1).setCellValue("FN");
            exampleRow.createCell(2).setCellValue("A1234567K");
            exampleRow.createCell(3).setCellValue("Client Exemple");
            exampleRow.createCell(4).setCellValue("ART001");
            exampleRow.createCell(5).setCellValue("Produit Test");
            exampleRow.createCell(6).setCellValue(1000.00);
            exampleRow.createCell(7).setCellValue(2);
            exampleRow.createCell(8).setCellValue("A");
            exampleRow.createCell(9).setCellValue("CDF");
            exampleRow.createCell(10).setCellValue("0");
            
            // Ajuster la largeur
            for (int i = 0; i < INPUT_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Ajouter une feuille d'instructions
            Sheet instructionSheet = workbook.createSheet("Instructions");
            addInstructions(instructionSheet, workbook);
            
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Ajoute une feuille d'instructions
     */
    private void addInstructions(Sheet sheet, Workbook workbook) {
        CellStyle boldStyle = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        boldStyle.setFont(boldFont);
        
        String[][] instructions = {
            {"Colonne", "Description", "Obligatoire", "Valeurs possibles"},
            {"rn", "Numéro de référence de la facture", "OUI", "Texte unique"},
            {"type", "Type de facture", "NON", "FN (Normal), FA (Avoir), FP (Proforma)"},
            {"clientNif", "NIF du client", "NON", "Format: A1234567K"},
            {"clientName", "Nom du client", "NON", "Texte"},
            {"itemCode", "Code de l'article", "NON", "Texte"},
            {"itemName", "Nom de l'article", "NON", "Texte"},
            {"itemPrice", "Prix unitaire", "OUI", "Nombre décimal"},
            {"itemQuantity", "Quantité", "OUI", "Nombre décimal"},
            {"itemTaxGroup", "Groupe de TVA", "NON", "A (16%), B (8%), C (0%), D (Exonéré)"},
            {"currency", "Devise", "NON", "CDF, USD"},
            {"mode", "Mode de facturation", "NON", "0 (normal), 1 (spécial)"}
        };
        
        for (int i = 0; i < instructions.length; i++) {
            Row row = sheet.createRow(i);
            for (int j = 0; j < instructions[i].length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue(instructions[i][j]);
                if (i == 0) {
                    cell.setCellStyle(boldStyle);
                }
            }
        }
        
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // === MÉTHODES UTILITAIRES ===

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < 11; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getStringValue(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getStringValue(Cell cell) {
        if (cell == null) return null;
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private BigDecimal getBigDecimalValue(Cell cell) {
        if (cell == null) return null;
        
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) yield null;
                    yield new BigDecimal(value.replace(",", "."));
                }
                default -> null;
            };
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, "Erreur de conversion numérique: " + e.getMessage());
            return null;
        }
    }

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void createNumericCell(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createValidStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createInvalidStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDuplicateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
