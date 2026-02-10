package org.middleware.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.middleware.models.InvoiceEntity;

import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class ExcelTraitement {

    /**
     * Met à jour le fichier Excel avec les données des factures normalisées
     * Chaque ligne Excel correspondant à une facture sera mise à jour avec les nouvelles valeurs
     */
    public byte[] updateExcelFromInvoiceEntities(List<InvoiceEntity> invoices, byte[] originalExcel) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(originalExcel);
            Workbook workbook = new XSSFWorkbook(bis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            
            // Créer un Map pour accéder rapidement aux factures par RN
            Map<String, InvoiceEntity> invoiceMap = invoices.stream()
                    .filter(inv -> inv.rn != null)
                    .collect(Collectors.toMap(inv -> inv.rn, inv -> inv));
            
            // Parcourir toutes les lignes Excel (en sautant l'en-tête)
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                
                // Récupérer le RN de la ligne Excel
                String excelRn = getStringCellValue(row.getCell(0)); // Colonne A: rn
                
                if (excelRn != null && invoiceMap.containsKey(excelRn)) {
                    InvoiceEntity invoice = invoiceMap.get(excelRn);
                    updateExcelRowFromInvoice(row, invoice);
                }
            }
            
            // Retourner le fichier Excel mis à jour
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.write(bos);
                return bos.toByteArray();
            }
        }
    }

    /**
     * Met à jour une ligne Excel spécifique avec les données d'une InvoiceEntity
     * Cette méthode suit la même structure de colonnes que votre code existant
     */
    private void updateExcelRowFromInvoice(Row row, InvoiceEntity invoice) {
        int colIndex = 1;
        
        // Colonne A: rn (ne change pas)
        setCellValue(row, colIndex++, invoice.rn);
        
        // Colonne B: type
        setCellValue(row, colIndex++, invoice.type);
        
        // Colonne C: clientNif
        setCellValue(row, colIndex++, invoice.client != null ? invoice.client.nif : null);
        
        // Colonne D: clientName
        setCellValue(row, colIndex++, invoice.client != null ? invoice.client.name : null);
        
        // Colonne E: clientType
        setCellValue(row, colIndex++, invoice.client != null ? invoice.client.type : null);
        
        // Si la facture a plusieurs items, on prend le premier pour la mise à jour
        // (ou vous pouvez adapter selon votre logique métier)
        InvoiceEntity.Item item = invoice.items != null && !invoice.items.isEmpty() 
                ? invoice.items.get(0) 
                : null;
        
        // Colonne F: itemCode
        setCellValue(row, colIndex++, item != null ? item.code : null);
        
        // Colonne G: itemName
        setCellValue(row, colIndex++, item != null ? item.name : null);
        
        // Colonne H: itemPrice
        setCellValue(row, colIndex++, item != null ? item.price : null);
        
        // Colonne I: itemQuantity
        setCellValue(row, colIndex++, item != null ? item.quantity : null);
        
        // Colonne J: itemTaxGroup
        setCellValue(row, colIndex++, item != null ? item.taxGroup : null);
        
        // Colonne K: itemArticleType - déduit du type
        String articleType = "SER".equals(item != null ? item.type : null) ? "SER" : "BIE";
        setCellValue(row, colIndex++, articleType);
        
        // Colonne L: unitPriceMode
        // Utilisez votre logique pour déterminer le mode de prix
        String unitPriceMode = "ht"; // Par défaut
        if (invoice.mode != null) {
            unitPriceMode = "ht".equalsIgnoreCase(invoice.mode) ? "ht" : "ttc";
        }
        setCellValue(row, colIndex++, unitPriceMode);
        
        // Colonne M: currency
        setCellValue(row, colIndex++, invoice.currency);
        
        // Colonne N: unit
        // Vous pouvez conserver la valeur originale ou laisser vide
        // setCellValue(row, colIndex++, ""); // Laisser inchangé
        
        // Colonne O: specificTaxAmount
        BigDecimal taxAmount = item != null ? item.taxSpecificAmount : null;
        setCellValue(row, colIndex++, taxAmount);
        
        // Colonne P: taxSpecificValue
        // Stockez la valeur de taxe spécifique si disponible
        String taxValue = item != null ? item.taxSpecificValue : null;
        setCellValue(row, colIndex++, taxValue);
        
        // Colonne Q: mode
        setCellValue(row, colIndex++, invoice.mode);
        
        // Colonne R: reference
        setCellValue(row, colIndex++, invoice.reference);
        
        // Colonne S: referenceType
        setCellValue(row, colIndex++, invoice.referenceType);
        
        // Colonne T: referenceDesc
        setCellValue(row, colIndex++, invoice.referenceDesc);
        
        // Colonne U: curCode
        setCellValue(row, colIndex++, invoice.curCode);
        
        // Colonne V: curDate
        setCellValue(row, colIndex++, invoice.curDate);
        
        // Colonne W: curRate
        setCellValue(row, colIndex++, invoice.curRate);
        
        // Colonne X: errorCode
        setCellValue(row, colIndex++, invoice.errorCode);
        
        // Colonne Y: errorDesc
        setCellValue(row, colIndex++, invoice.errorDesc);
        
        // Colonne Z: dateTime
        setCellValue(row, colIndex++, invoice.dateTime);
        
        // Colonne AA: qrCode
        setCellValue(row, colIndex++, invoice.qrCode);
        
        // Colonne AB: codeDEFDGI
        setCellValue(row, colIndex++, invoice.codeDEFDGI);
        
        // Colonne AC: counters
        setCellValue(row, colIndex++, invoice.counters);
        
        // Colonne AD: nim
        setCellValue(row, colIndex++, invoice.nim);
        
        // Mettre à jour les totaux si nécessaire (dans les cellules correspondantes)
        updateCalculatedFields(row, invoice);
    }

    /**
     * Met à jour les champs calculés dans Excel
     */
    private void updateCalculatedFields(Row row, InvoiceEntity invoice) {
        // Si vous avez des colonnes pour les totaux, les mettre à jour ici
        // Par exemple, si vous avez une colonne pour le total:
        // setCellValue(row, totalColumnIndex, invoice.total);
        
        // Vous pouvez aussi recalculer les valeurs basées sur les nouvelles données
        if (invoice.items != null && !invoice.items.isEmpty()) {
            InvoiceEntity.Item item = invoice.items.get(0);
            if (item != null && item.price != null && item.quantity != null) {
                // Calculer et mettre à jour le montant total de l'article
                BigDecimal itemTotal = item.price.multiply(item.quantity);
                // setCellValue(row, itemTotalColumnIndex, itemTotal);
            }
        }
    }

    /**
     * Méthodes utilitaires pour mettre à jour les cellules
     */
    private void setCellValue(Row row, int colIndex, String value) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            cell = row.createCell(colIndex);
        }
        
        if (value != null) {
            cell.setCellValue(value);
        } else {
            cell.setCellValue("");
        }
    }

    private void setCellValue(Row row, int colIndex, BigDecimal value) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            cell = row.createCell(colIndex);
        }
        
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0);
        }
    }

    private void setCellValue(Row row, int colIndex, LocalDateTime value) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            cell = row.createCell(colIndex);
        }
        
        if (value != null) {
            cell.setCellValue(value);
        } else {
            cell.setCellValue("");
        }
    }

    /**
     * Méthode utilitaire pour lire les valeurs des cellules (identique à votre code)
     */
    private String getStringCellValue(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (isDateCell(cell)) {
                    try {
                        return cell.getLocalDateTimeCellValue().toString();
                    } catch (Exception e) {
                        return cell.getDateCellValue().toString();
                    }
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num) && !Double.isInfinite(num)) {
                        return String.valueOf((int) num);
                    }
                    return BigDecimal.valueOf(num).stripTrailingZeros().toPlainString();
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    switch (cell.getCachedFormulaResultType()) {
                        case STRING:
                            return cell.getStringCellValue().trim();
                        case NUMERIC:
                            if (isDateCell(cell)) {
                                return cell.getLocalDateTimeCellValue().toString();
                            } else {
                                double num = cell.getNumericCellValue();
                                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                                    return String.valueOf((int) num);
                                }
                                return BigDecimal.valueOf(num).stripTrailingZeros().toPlainString();
                            }
                        case BOOLEAN:
                            return String.valueOf(cell.getBooleanCellValue());
                        default:
                            return "";
                    }
                } catch (Exception e) {
                    return "";
                }
            default:
                return null;
        }
    }

    /**
     * Méthode pour détecter si une cellule contient une date
     */
    private boolean isDateCell(Cell cell) {
        if (cell == null) return false;
        
        try {
            CellStyle style = cell.getCellStyle();
            String format = style.getDataFormatString();
            
            return format != null && (
                format.contains("d") || format.contains("m") || format.contains("y") ||
                format.contains("D") || format.contains("M") || format.contains("Y") ||
                format.contains("/") || format.contains("-") ||
                format.toLowerCase().contains("date") ||
                format.equals("m/d/yy") || format.equals("dd/mm/yyyy") ||
                format.equals("yyyy-mm-dd") || format.equals("general")
            );
        } catch (Exception e) {
            return false;
        }
    }

}
