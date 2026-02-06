package org.middleware.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.middleware.dto.ExcelInvoiceRow;
import org.middleware.models.Entreprise;

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

/**
 * Service pour traiter les fichiers Excel de factures conformes aux spécifications SFE.
 * - Lit le fichier Excel uploadé
 * - Normalise et valide les données selon les règles SFE
 * - Ajoute les informations de l'entreprise
 * - Retourne un fichier Excel enrichi
 */
@ApplicationScoped
public class ExcelInvoiceService {

    private static final Logger LOG = Logger.getLogger(ExcelInvoiceService.class.getName());

    // Colonnes d'entrée (fournies par l'utilisateur) - 15 colonnes
    private static final String[] INPUT_HEADERS = {
        "rn", "type", "clientNif", "clientName", 
        "itemCode", "itemName", "itemPrice", "itemQuantity", 
        "itemTaxGroup", "itemArticleType", "unitPriceMode",
        "currency", "unit", "specificTaxAmount", "mode"
    };

    // Colonnes de sortie (toutes les colonnes) - 24 colonnes
    private static final String[] OUTPUT_HEADERS = {
        "rn", "type", "clientNif", "clientName", 
        "itemCode", "itemName", "itemPrice", "itemQuantity", 
        "itemTaxGroup", "itemArticleType", "unitPriceMode",
        "currency", "unit", "specificTaxAmount", "mode",
        "nif", "isf", "companyName", "taxRate", "subtotal", "taxAmount", "total", 
        "status", "errorMessage"
    };

    /**
     * Traite un fichier Excel et retourne un fichier enrichi
     * 
     * @param inputStream Le fichier Excel uploadé
     * @param entreprise L'entreprise de l'utilisateur connecté
     * @return byte[] Le fichier Excel enrichi
     */
    public byte[] processExcelFile(InputStream inputStream, Entreprise entreprise) throws IOException {
        LOG.info("=== Début du traitement Excel SFE pour: " + entreprise.nom + " ===");
        
        List<ExcelInvoiceRow> rows = readExcelFile(inputStream);
        LOG.info("Lignes lues: " + rows.size());
        
        // Enrichir chaque ligne avec les données de l'entreprise
        Set<String> seenRns = new HashSet<>();
        int validCount = 0, invalidCount = 0, duplicateCount = 0;
        
        for (ExcelInvoiceRow row : rows) {
            // Ajouter les infos de l'entreprise
            row.nif = entreprise.nif;
            row.isf = entreprise.isf;
            row.companyName = entreprise.nom;
            
            // Normaliser les valeurs
            row.normalize();
            
            // Vérifier les doublons
            if (row.rn != null && seenRns.contains(row.rn)) {
                row.status = "DUPLICATE";
                row.errorMessage = "Numéro de facture en double dans le fichier";
                duplicateCount++;
            } else {
                if (row.rn != null) {
                    seenRns.add(row.rn);
                }
                // Valider la ligne selon les règles SFE
                if (row.validate()) {
                    validCount++;
                } else {
                    invalidCount++;
                }
            }
            
            // Calculer les totaux si valide
            if ("VALID".equals(row.status)) {
                row.calculateTotals();
            }
        }
        
        LOG.info("=== Traitement terminé: " + validCount + " valides, " + invalidCount + " invalides, " + duplicateCount + " doublons ===");
        
        // Générer le fichier Excel de sortie
        return writeExcelFile(rows);
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
     * Parse une ligne Excel en ExcelInvoiceRow avec les nouvelles colonnes SFE
     */
    private ExcelInvoiceRow parseRow(Row row) {
        ExcelInvoiceRow invoiceRow = new ExcelInvoiceRow();
        
        // Colonnes d'entrée (15 colonnes)
        invoiceRow.rn = getStringValue(row.getCell(0));
        invoiceRow.type = getStringValue(row.getCell(1));
        invoiceRow.clientNif = getStringValue(row.getCell(2));
        invoiceRow.clientName = getStringValue(row.getCell(3));
        invoiceRow.itemCode = getStringValue(row.getCell(4));
        invoiceRow.itemName = getStringValue(row.getCell(5));
        invoiceRow.itemPrice = getBigDecimalValue(row.getCell(6));
        invoiceRow.itemQuantity = getBigDecimalValue(row.getCell(7));
        invoiceRow.itemTaxGroup = getStringValue(row.getCell(8));
        invoiceRow.itemArticleType = getStringValue(row.getCell(9));
        invoiceRow.unitPriceMode = getStringValue(row.getCell(10));
        invoiceRow.currency = getStringValue(row.getCell(11));
        invoiceRow.unit = getStringValue(row.getCell(12));
        invoiceRow.specificTaxAmount = getBigDecimalValue(row.getCell(13));
        invoiceRow.mode = getStringValue(row.getCell(14));
        
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
            CellStyle percentStyle = createPercentStyle(workbook);
            
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
                
                // Colonnes d'entrée (15)
                createCell(row, 0, invoiceRow.rn, rowStyle);
                createCell(row, 1, invoiceRow.type, rowStyle);
                createCell(row, 2, invoiceRow.clientNif, rowStyle);
                createCell(row, 3, invoiceRow.clientName, rowStyle);
                createCell(row, 4, invoiceRow.itemCode, rowStyle);
                createCell(row, 5, invoiceRow.itemName, rowStyle);
                createNumericCell(row, 6, invoiceRow.itemPrice, numberStyle);
                createNumericCell(row, 7, invoiceRow.itemQuantity, numberStyle);
                createCell(row, 8, invoiceRow.itemTaxGroup, rowStyle);
                createCell(row, 9, invoiceRow.itemArticleType, rowStyle);
                createCell(row, 10, invoiceRow.unitPriceMode, rowStyle);
                createCell(row, 11, invoiceRow.currency, rowStyle);
                createCell(row, 12, invoiceRow.unit, rowStyle);
                createNumericCell(row, 13, invoiceRow.specificTaxAmount, numberStyle);
                createCell(row, 14, invoiceRow.mode, rowStyle);
                
                // Colonnes ajoutées (9)
                createCell(row, 15, invoiceRow.nif, rowStyle);
                createCell(row, 16, invoiceRow.isf, rowStyle);
                createCell(row, 17, invoiceRow.companyName, rowStyle);
                createNumericCell(row, 18, invoiceRow.taxRate, percentStyle);
                createNumericCell(row, 19, invoiceRow.subtotal, numberStyle);
                createNumericCell(row, 20, invoiceRow.taxAmount, numberStyle);
                createNumericCell(row, 21, invoiceRow.total, numberStyle);
                createCell(row, 22, invoiceRow.status, rowStyle);
                createCell(row, 23, invoiceRow.errorMessage, rowStyle);
            }
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < OUTPUT_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Ajouter la feuille de référence des groupes TVA
            addTaxGroupReferenceSheet(workbook);
            
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Génère un fichier Excel template vide avec les spécifications SFE
     */
    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Template Factures");
            
            // Style pour les en-têtes
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle requiredStyle = createRequiredHeaderStyle(workbook);
            
            // En-têtes (colonnes d'entrée uniquement)
            Row headerRow = sheet.createRow(0);
            String[] requiredCols = {"rn", "itemCode", "itemName", "itemPrice", "itemQuantity"};
            
            for (int i = 0; i < INPUT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(INPUT_HEADERS[i]);
                
                // Style différent pour les colonnes obligatoires
                boolean isRequired = false;
                for (String req : requiredCols) {
                    if (req.equals(INPUT_HEADERS[i])) {
                        isRequired = true;
                        break;
                    }
                }
                cell.setCellStyle(isRequired ? requiredStyle : headerStyle);
            }
            
            // Ligne d'exemple 1: Bien avec TVA 16%
            Row exampleRow1 = sheet.createRow(1);
            exampleRow1.createCell(0).setCellValue("FAC-2026-001");
            exampleRow1.createCell(1).setCellValue("FN");
            exampleRow1.createCell(2).setCellValue("A1234567K");
            exampleRow1.createCell(3).setCellValue("Client Exemple");
            exampleRow1.createCell(4).setCellValue("PROD-001");
            exampleRow1.createCell(5).setCellValue("Ordinateur portable");
            exampleRow1.createCell(6).setCellValue(500000);
            exampleRow1.createCell(7).setCellValue(2);
            exampleRow1.createCell(8).setCellValue("B");      // Taxable 16%
            exampleRow1.createCell(9).setCellValue("BIE");    // Bien
            exampleRow1.createCell(10).setCellValue("HT");
            exampleRow1.createCell(11).setCellValue("CDF");
            exampleRow1.createCell(12).setCellValue("pcs");
            exampleRow1.createCell(13).setCellValue(0);
            exampleRow1.createCell(14).setCellValue("0");
            
            // Ligne d'exemple 2: Service avec TVA 8%
            Row exampleRow2 = sheet.createRow(2);
            exampleRow2.createCell(0).setCellValue("FAC-2026-002");
            exampleRow2.createCell(1).setCellValue("FN");
            exampleRow2.createCell(2).setCellValue("");
            exampleRow2.createCell(3).setCellValue("Client Sans NIF");
            exampleRow2.createCell(4).setCellValue("SERV-001");
            exampleRow2.createCell(5).setCellValue("Consultation IT");
            exampleRow2.createCell(6).setCellValue(150000);
            exampleRow2.createCell(7).setCellValue(5);
            exampleRow2.createCell(8).setCellValue("C");      // Taxable 8%
            exampleRow2.createCell(9).setCellValue("SER");    // Service
            exampleRow2.createCell(10).setCellValue("HT");
            exampleRow2.createCell(11).setCellValue("CDF");
            exampleRow2.createCell(12).setCellValue("heure");
            exampleRow2.createCell(13).setCellValue(0);
            exampleRow2.createCell(14).setCellValue("0");
            
            // Ligne d'exemple 3: Taxe (groupe L ou N)
            Row exampleRow3 = sheet.createRow(3);
            exampleRow3.createCell(0).setCellValue("FAC-2026-003");
            exampleRow3.createCell(1).setCellValue("FN");
            exampleRow3.createCell(2).setCellValue("");
            exampleRow3.createCell(3).setCellValue("");
            exampleRow3.createCell(4).setCellValue("TAX-001");
            exampleRow3.createCell(5).setCellValue("Prélèvement sur vente");
            exampleRow3.createCell(6).setCellValue(10000);
            exampleRow3.createCell(7).setCellValue(1);
            exampleRow3.createCell(8).setCellValue("L");      // Prélèvements
            exampleRow3.createCell(9).setCellValue("TAX");    // Taxes (obligatoire pour L et N)
            exampleRow3.createCell(10).setCellValue("HT");
            exampleRow3.createCell(11).setCellValue("CDF");
            exampleRow3.createCell(12).setCellValue("pcs");
            exampleRow3.createCell(13).setCellValue(0);
            exampleRow3.createCell(14).setCellValue("0");
            
            // Ajuster la largeur
            for (int i = 0; i < INPUT_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Ajouter la feuille d'instructions
            addInstructionsSheet(workbook);
            
            // Ajouter la feuille de référence des groupes TVA
            addTaxGroupReferenceSheet(workbook);
            
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Ajoute une feuille d'instructions détaillées
     */
    private void addInstructionsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Instructions");
        
        CellStyle boldStyle = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        boldStyle.setFont(boldFont);
        
        CellStyle requiredStyle = workbook.createCellStyle();
        Font requiredFont = workbook.createFont();
        requiredFont.setBold(true);
        requiredFont.setColor(IndexedColors.RED.getIndex());
        requiredStyle.setFont(requiredFont);
        
        String[][] instructions = {
            {"Colonne", "Description", "Obligatoire", "Valeurs possibles"},
            {"rn", "Numéro de référence de la facture", "OUI *", "Texte unique (ex: FAC-2026-001)"},
            {"type", "Type de facture", "NON", "FN (Normal), FA (Avoir), FP (Proforma)"},
            {"clientNif", "NIF du client", "NON", "Format: A1234567K"},
            {"clientName", "Nom du client", "NON", "Texte"},
            {"itemCode", "Code de l'article", "OUI *", "Texte unique (obligatoire SFE)"},
            {"itemName", "Nom de l'article", "OUI *", "Texte"},
            {"itemPrice", "Prix unitaire", "OUI *", "Nombre décimal > 0"},
            {"itemQuantity", "Quantité", "OUI *", "Nombre décimal > 0"},
            {"itemTaxGroup", "Groupe de TVA (SFE)", "NON", "A-N (voir feuille 'Groupes TVA')"},
            {"itemArticleType", "Type d'article (SFE)", "NON", "BIE (Bien), SER (Service), TAX (Taxes)"},
            {"unitPriceMode", "Mode de prix", "NON", "HT (Hors Taxe), TTC (Toutes Taxes Comprises)"},
            {"currency", "Devise", "NON", "CDF, USD, DH"},
            {"unit", "Unité de mesure", "NON", "pcs, kg, heure, etc."},
            {"specificTaxAmount", "Montant taxe spécifique", "NON", "Nombre décimal (par unité)"},
            {"mode", "Mode de facturation", "NON", "0 (normal), 1 (spécial)"},
            {"", "", "", ""},
            {"⚠️ CONTRAINTE SFE IMPORTANTE:", "", "", ""},
            {"", "Type TAX uniquement autorisé pour groupes L et N", "", ""},
            {"", "Groupes L et N exigent obligatoirement type TAX", "", ""}
        };
        
        for (int i = 0; i < instructions.length; i++) {
            Row row = sheet.createRow(i);
            for (int j = 0; j < instructions[i].length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue(instructions[i][j]);
                if (i == 0 || instructions[i][2].contains("*")) {
                    cell.setCellStyle(i == 0 ? boldStyle : (j == 2 ? requiredStyle : null));
                }
            }
        }
        
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Ajoute une feuille de référence des groupes TVA SFE
     */
    private void addTaxGroupReferenceSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Groupes TVA SFE");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        String[][] taxGroups = {
            {"Groupe", "Description", "Taux TVA", "Types Article Autorisés"},
            {"A", "Exonéré", "0%", "BIE, SER"},
            {"B", "Taxable 16%", "16%", "BIE, SER"},
            {"C", "Taxable 8%", "8%", "BIE, SER"},
            {"D", "Régimes dérogatoires TVA", "0%", "BIE, SER"},
            {"E", "Exportation et opérations assimilées", "0%", "BIE, SER"},
            {"F", "TVA marché public à financement extérieur", "16%", "BIE, SER"},
            {"G", "TVA marché public à financement extérieur", "8%", "BIE, SER"},
            {"H", "Consignation/déconsignation d'emballage", "0%", "BIE, SER"},
            {"I", "Garantie et caution", "0%", "BIE, SER"},
            {"J", "Débours", "0%", "BIE, SER"},
            {"K", "Opérations réalisées par les non assujettis", "0%", "BIE, SER"},
            {"L", "Prélèvements sur ventes", "0%", "TAX uniquement ⚠️"},
            {"M", "Ventes réglementées avec TVA spécifique", "0%", "BIE, SER"},
            {"N", "TVA spécifique", "0%", "TAX uniquement ⚠️"},
            {"", "", "", ""},
            {"Types d'Article:", "", "", ""},
            {"BIE", "Bien - pour tous groupes sauf L et N", "", ""},
            {"SER", "Service - pour tous groupes sauf L et N", "", ""},
            {"TAX", "Taxes et redevances - UNIQUEMENT pour groupes L et N", "", ""}
        };
        
        for (int i = 0; i < taxGroups.length; i++) {
            Row row = sheet.createRow(i);
            for (int j = 0; j < taxGroups[i].length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue(taxGroups[i][j]);
                if (i == 0 || i == 16) {
                    cell.setCellStyle(headerStyle);
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
        for (int i = 0; i < 15; i++) {
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

    private CellStyle createRequiredHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.RED.getIndex());
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

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0\"%\""));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
