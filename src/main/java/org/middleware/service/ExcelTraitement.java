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

    private static final String[] ROOT_HEADERS = {"EMAIL", "NIF", "COMPANY_NAME", "ISF"};
    private static final String[] COMMENT_HEADERS = {"CMTA", "CMTB", "CMTC", "CMTD", "CMTE", "CMTF", "CMTG", "CMTH"};
    private static final String[] PAYMENT_HEADERS = {"OPERATOR_ID", "OPERATOR_NAME", "PAYMENT_NAME", "PAYMENT_AMOUNT", "PAYMENT_CURRENCY_CODE", "PAYMENT_CURRENCY_RATE"};
    private static final String[] RESULT_HEADERS = {"UID", "TOTAL", "CUR_TOTAL", "VTOTAL", "ERROR_CODE", "ERROR_DESC", "DATE_TIME", "QR_CODE", "CODE_DEF_DGI", "COUNTERS", "NIM"};
    private static final int ROOT_COLUMN_COUNT = ROOT_HEADERS.length;
    private static final int COMMENT_COLUMN_COUNT = COMMENT_HEADERS.length;
    private static final int PAYMENT_COLUMN_COUNT = PAYMENT_HEADERS.length;
    private static final int RN_COLUMN = ROOT_COLUMN_COUNT;
    private static final int COMMENT_COLUMN = ROOT_COLUMN_COUNT + 17;
    private static final int PAYMENT_COLUMN = ROOT_COLUMN_COUNT + 31;
    private static final int RESULT_COLUMN = PAYMENT_COLUMN + PAYMENT_COLUMN_COUNT;

    /**
     * Met à jour le fichier Excel avec les données des factures normalisées
     * Chaque ligne Excel correspondant à une facture sera mise à jour avec les nouvelles valeurs
     */
    public byte[] updateExcelFromInvoiceEntities(List<InvoiceEntity> invoices, byte[] originalExcel) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(originalExcel);
            Workbook workbook = new XSSFWorkbook(bis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            ensureInvoiceEntityColumns(sheet);
            
            // Créer un Map pour accéder rapidement aux factures par RN
            Map<String, InvoiceEntity> invoiceMap = invoices.stream()
                    .filter(inv -> inv.rn != null)
                    .collect(Collectors.toMap(inv -> inv.rn, inv -> inv, (first, replacement) -> replacement));
            
            // Parcourir toutes les lignes Excel (en sautant l'en-tête)
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;
                
                // Récupérer le RN de la ligne Excel
                String excelRn = getStringCellValue(row.getCell(RN_COLUMN)); // Colonne F: rn
                
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
        int colIndex = 0;
        
        setCellValue(row, colIndex++, invoice.email);
        setCellValue(row, colIndex++, invoice.nif);
        setCellValue(row, colIndex++, invoice.companyName);
        setCellValue(row, colIndex++, invoice.isf);

        // Colonne F: rn
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
        InvoiceEntity.Item item = findMatchingItem(row, invoice);
        
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
        setCellValue(row, colIndex++, item != null ? item.type : null);
        
        // Colonne L: unitPriceMode
        // Utilisez votre logique pour déterminer le mode de prix
        String unitPriceMode = "ht"; // Par défaut
        if (invoice.mode != null) {
            unitPriceMode = "ht".equalsIgnoreCase(invoice.mode) ? "ht" : "ttc";
        }
        setCellValue(row, colIndex++, unitPriceMode);
        
        // Colonne M: currency
        setCellValue(row, colIndex++, invoice.currency);
        setCellValue(row, colIndex++, item != null ? item.unit : null);
        
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

        setCellValue(row, colIndex++, invoice.cmta);
        setCellValue(row, colIndex++, invoice.cmtb);
        setCellValue(row, colIndex++, invoice.cmtc);
        setCellValue(row, colIndex++, invoice.cmtd);
        setCellValue(row, colIndex++, invoice.cmte);
        setCellValue(row, colIndex++, invoice.cmtf);
        setCellValue(row, colIndex++, invoice.cmtg);
        setCellValue(row, colIndex++, invoice.cmth);
        
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

        setCellValue(row, colIndex++, invoice.operator != null && invoice.operator.id != null ? invoice.operator.id.toString() : null);
        setCellValue(row, colIndex++, invoice.operator != null ? invoice.operator.name : null);
        InvoiceEntity.Payment payment = invoice.payments != null && !invoice.payments.isEmpty() ? invoice.payments.get(0) : null;
        setCellValue(row, colIndex++, payment != null ? payment.name : "ESPECES");
        setCellValue(row, colIndex++, payment != null ? payment.amount : invoice.total);
        setCellValue(row, colIndex++, payment != null ? payment.currencyCode : null);
        setCellValue(row, colIndex++, payment != null ? payment.currencyRate : null);
        
        setCellValue(row, colIndex++, invoice.uid);
        setCellValue(row, colIndex++, invoice.total);
        setCellValue(row, colIndex++, invoice.curTotal);
        setCellValue(row, colIndex++, invoice.vtotal);

        // Colonne reponse DGI: errorCode
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

    private InvoiceEntity.Item findMatchingItem(Row row, InvoiceEntity invoice) {
        if (invoice.items == null || invoice.items.isEmpty()) {
            return null;
        }

        String itemCode = getStringCellValue(row.getCell(ROOT_COLUMN_COUNT + 5));
        String itemName = getStringCellValue(row.getCell(ROOT_COLUMN_COUNT + 6));
        for (InvoiceEntity.Item item : invoice.items) {
            if (itemCode != null && item.code != null && itemCode.equalsIgnoreCase(item.code)) {
                return item;
            }
        }
        for (InvoiceEntity.Item item : invoice.items) {
            if (itemName != null && item.name != null && itemName.equalsIgnoreCase(item.name)) {
                return item;
            }
        }
        return invoice.items.get(0);
    }

    private void ensureInvoiceEntityColumns(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            header = sheet.createRow(0);
        }

        String firstHeader = getStringCellValue(header.getCell(0));
        if ("EMAIL".equalsIgnoreCase(firstHeader)) {
            String secondHeader = getStringCellValue(header.getCell(1));
            if ("UID".equalsIgnoreCase(secondHeader)) {
                shiftColumnsLeft(sheet, 1, 1);
                header = sheet.getRow(0);
            }
            for (int i = 0; i < ROOT_HEADERS.length; i++) {
                setCellValue(header, i, ROOT_HEADERS[i]);
            }
            ensureCommentColumns(sheet);
            ensurePaymentColumns(sheet);
            setResultHeaders(header);
            return;
        }

        for (int rowNum = 0; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                row = sheet.createRow(rowNum);
            }

            int lastCell = Math.max(row.getLastCellNum(), 0);
            for (int col = lastCell - 1; col >= 0; col--) {
                Cell oldCell = row.getCell(col);
                Cell newCell = row.getCell(col + ROOT_COLUMN_COUNT);
                if (newCell == null) {
                    newCell = row.createCell(col + ROOT_COLUMN_COUNT);
                }
                copyCellValue(oldCell, newCell);
                if (oldCell != null) {
                    oldCell.setBlank();
                }
            }
        }

        header = sheet.getRow(0);
        for (int i = 0; i < ROOT_HEADERS.length; i++) {
            setCellValue(header, i, ROOT_HEADERS[i]);
        }
        ensureCommentColumns(sheet);
        ensurePaymentColumns(sheet);
        setResultHeaders(header);
    }

    private void ensureCommentColumns(Sheet sheet) {
        Row header = sheet.getRow(0);
        String firstCommentHeader = getStringCellValue(header.getCell(COMMENT_COLUMN));
        if ("CMTA".equalsIgnoreCase(firstCommentHeader)) {
            setCommentHeaders(header);
            return;
        }

        shiftColumnsRight(sheet, COMMENT_COLUMN, COMMENT_COLUMN_COUNT);
        header = sheet.getRow(0);
        setCommentHeaders(header);
    }

    private void setCommentHeaders(Row header) {
        for (int i = 0; i < COMMENT_HEADERS.length; i++) {
            setCellValue(header, COMMENT_COLUMN + i, COMMENT_HEADERS[i]);
        }
    }

    private void ensurePaymentColumns(Sheet sheet) {
        Row header = sheet.getRow(0);
        String firstPaymentHeader = getStringCellValue(header.getCell(PAYMENT_COLUMN));
        if ("OPERATOR_ID".equalsIgnoreCase(firstPaymentHeader)) {
            setPaymentHeaders(header);
            return;
        }

        shiftColumnsRight(sheet, PAYMENT_COLUMN, PAYMENT_COLUMN_COUNT);
        header = sheet.getRow(0);
        setPaymentHeaders(header);
    }

    private void setPaymentHeaders(Row header) {
        for (int i = 0; i < PAYMENT_HEADERS.length; i++) {
            setCellValue(header, PAYMENT_COLUMN + i, PAYMENT_HEADERS[i]);
        }
    }

    private void shiftColumnsRight(Sheet sheet, int startColumn, int columnCount) {
        for (int rowNum = 0; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                row = sheet.createRow(rowNum);
            }

            int lastCell = Math.max(row.getLastCellNum(), 0);
            for (int col = lastCell - 1; col >= startColumn; col--) {
                Cell oldCell = row.getCell(col);
                Cell newCell = row.getCell(col + columnCount);
                if (newCell == null) {
                    newCell = row.createCell(col + columnCount);
                }
                copyCellValue(oldCell, newCell);
                if (oldCell != null) {
                    oldCell.setBlank();
                }
            }
        }
    }

    private void shiftColumnsLeft(Sheet sheet, int startColumn, int columnCount) {
        for (int rowNum = 0; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                continue;
            }

            int lastCell = Math.max(row.getLastCellNum(), 0);
            for (int col = startColumn; col < lastCell; col++) {
                Cell source = row.getCell(col + columnCount);
                Cell target = row.getCell(col);
                if (target == null) {
                    target = row.createCell(col);
                }
                copyCellValue(source, target);
            }

            for (int col = Math.max(lastCell - columnCount, startColumn); col < lastCell; col++) {
                Cell cell = row.getCell(col);
                if (cell != null) {
                    cell.setBlank();
                }
            }
        }
    }

    private void setResultHeaders(Row header) {
        for (int i = 0; i < RESULT_HEADERS.length; i++) {
            setCellValue(header, RESULT_COLUMN + i, RESULT_HEADERS[i]);
        }
    }

    private void copyCellValue(Cell source, Cell target) {
        if (source == null) {
            target.setBlank();
            return;
        }

        switch (source.getCellType()) {
            case STRING:
                target.setCellValue(source.getStringCellValue());
                break;
            case NUMERIC:
                if (isDateCell(source)) {
                    target.setCellValue(source.getLocalDateTimeCellValue());
                } else {
                    target.setCellValue(source.getNumericCellValue());
                }
                break;
            case BOOLEAN:
                target.setCellValue(source.getBooleanCellValue());
                break;
            case FORMULA:
                target.setCellFormula(source.getCellFormula());
                break;
            case BLANK:
            default:
                target.setBlank();
                break;
        }
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
