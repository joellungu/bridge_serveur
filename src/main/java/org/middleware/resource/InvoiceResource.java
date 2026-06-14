package org.middleware.resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.middleware.dto.ApiResponse;
import org.middleware.models.ApiClient;
import org.middleware.models.Entreprise;
import org.middleware.models.InvoiceEntity;
import org.middleware.service.DgiService;
import org.middleware.service.InvoiceEntityResponseMapper;
import org.middleware.service.InvoiceValidator;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/api/invoice")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InvoiceResource {

    private static final Logger LOG = Logger.getLogger(InvoiceResource.class.getName());
    private static final String XLSX_REQUIRED_MESSAGE = "Le fichier doit etre un fichier Excel .xlsx";
    private static final int EXCEL_ROOT_COLUMN_COUNT = 4;
    private static final int EXCEL_COMMENT_COLUMN_COUNT = 8;
    private static final int EXCEL_PAYMENT_COLUMN_COUNT = 6;

    @Context
    ContainerRequestContext requestContext;

    @Inject
    JsonWebToken jwt;

    @Inject
    InvoiceValidator invoiceValidator;

    @GET
    @Path("test")
    @PermitAll
    public String test(){
        return "Ok";
    }

    @GET
    @Path("/all")
    public List<InvoiceEntity> list2() {

        //
        List<InvoiceEntity> invoices = InvoiceEntity.listAll();
        //
        return invoices;
    }

    @GET
    @Path("/debug")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject debug(@Context SecurityContext ctx) {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        // Subject
        if (ctx.getUserPrincipal() != null) {
            builder.add("subject", ctx.getUserPrincipal().getName());
            String email = jwt.getClaim("email");
            builder.add("email", email);
        }

        // Rôles
        builder.add("isUserInRole USER", ctx.isUserInRole("USER"));
        builder.add("isUserInRole ADMIN", ctx.isUserInRole("ADMIN"));

        // Claims via injection
        return builder.build();
    }

    /**
     * Soumet une facture à la DGI (Phase 1 + Phase 2)
     *
     * @param invoice La facture à soumettre
     * @return Réponse contenant l'InvoiceEntity mise à jour avec statut et erreurs
     */
    @POST
    @PermitAll
    @Transactional
    @Operation(summary = "Soumettre une facture à la DGI")
    public Response requestInvoice(InvoiceEntity invoice) {
        try {
            LOG.info("=== Réception soumission facture RN=" + (invoice != null ? invoice.rn : "null") + " ===");

            // 1. Validation de base
            if (invoice == null || invoice.rn == null || invoice.rn.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("RN_REQUIRED",
                                "Le numéro de facture (RN) est obligatoire"))
                        .build();
            }

            // 2. Récupération de l'utilisateur depuis le JWT
            if (requestContext == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("EMAIL_NOT_FOUND",
                                "Aucun email trouvé dans le token"))
                        .build();
            }

            Entreprise entreprise = getAuthenticatedEntreprise();
            if (entreprise == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("USER_NOT_FOUND",
                                "Aucun utilisateur trouvé pour cet email"))
                        .build();
            }

            // 3. Verifier si la facture existe deja
            InvoiceEntity existingInvoice = InvoiceEntity.find("nif = ?1 and rn = ?2", entreprise.nif, invoice.rn).firstResult();

            InvoiceEntity invoiceToProcess;
            if (existingInvoice == null) {
                // Nouvelle facture
                LOG.info("Nouvelle facture: " + invoice.rn);
                invoice.id = null;
                invoice.email = entreprise.email;
                invoice.nif = entreprise.nif;
                invoice.isf = entreprise.isf;
                invoice.status = "PENDING";
                List<String> validationErrors = invoiceValidator.validateForDgi(invoice);
                if (!validationErrors.isEmpty()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ApiResponse.error("INVOICE_VALIDATION_ERROR",
                                    String.join("; ", validationErrors)))
                            .build();
                }
                invoice.persist();
                invoiceToProcess = invoice;
            } else {
                // Facture existante
                LOG.info("Facture existante: " + invoice.rn + " (Status: " + existingInvoice.status + ")");

                // Vérifier si elle peut être retraitée
                if ("CONFIRMED".equals(existingInvoice.status)) {
                    return Response.status(200)
                            .entity(ApiResponse.error("INVOICE_ALREADY_CONFIRMED",
                                    "Cette facture a déjà été confirmée par la DGI"))
                            .build();
                }

                invoiceToProcess = existingInvoice;
            }

            // 4. Soumission à la DGI (Phase 1 + Phase 2)
            DgiService dgiService = CDI.current().select(DgiService.class).get();
            InvoiceEntity processedInvoice = dgiService.submitInvoice(invoiceToProcess, getDgiToken(entreprise));

            LOG.info("Facture traitée - Status: " + processedInvoice.status);

            // 5. Formater et retourner la réponse
            return Response.ok(
                    InvoiceEntityResponseMapper.toUserResponse(processedInvoice)
            ).build();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Exception lors du traitement: " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("INTERNAL_ERROR",
                            "Erreur interne lors du traitement: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Soumet un lot de factures à la DGI (Phase 1 + Phase 2 pour chaque facture)
     *
     * @param invoices Liste des factures à soumettre
     * @return Réponse contenant le résumé du traitement par lot avec succès et échecs
     */
    @POST
    @Path("batch")
    @PermitAll
    @Transactional
    @Operation(summary = "Soumettre un lot de factures à la DGI")
    public Response requestBatchInvoices(List<InvoiceEntity> invoices) {
        try {
            LOG.info("=== Réception traitement par lot de " + (invoices != null ? invoices.size() : 0) + " factures ===");

            // 1. Validation de base
            if (invoices == null || invoices.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("EMPTY_BATCH",
                                "La liste de factures est vide"))
                        .build();
            }

            // 2. Récupération de l'utilisateur depuis le JWT
            if (requestContext == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("EMAIL_NOT_FOUND",
                                "Aucun email trouvé dans le token"))
                        .build();
            }

            Entreprise entreprise = getAuthenticatedEntreprise();
            if (entreprise == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("USER_NOT_FOUND",
                                "Aucun utilisateur trouvé pour cet email"))
                        .build();
            }

            // 3. Traitement par lot
            List<Map<String, Object>> successResults = new ArrayList<>();
            List<Map<String, Object>> failureResults = new ArrayList<>();
            DgiService dgiService = CDI.current().select(DgiService.class).get();

            for (InvoiceEntity invoice : invoices) {
                try {
                    // Validation individuelle
                    if (invoice == null || invoice.rn == null || invoice.rn.trim().isEmpty()) {
                        Map<String, Object> failure = new HashMap<>();
                        failure.put("invoiceNumber", invoice != null ? invoice.rn : "null");
                        failure.put("error", "RN manquant ou invalide");
                        failureResults.add(failure);
                        continue;
                    }

                    LOG.info("Traitement facture lot: " + invoice.rn);

                    // Verifier si la facture existe deja
                    InvoiceEntity existingInvoice = InvoiceEntity.find("nif = ?1 and rn = ?2", entreprise.nif, invoice.rn).firstResult();

                    InvoiceEntity invoiceToProcess;
                    if (existingInvoice == null) {
                        // Nouvelle facture
                        invoice.id = null;
                        invoice.email = entreprise.email;
                        invoice.nif = entreprise.nif;
                        invoice.isf = entreprise.isf;
                        invoice.status = "PENDING";
                        List<String> validationErrors = invoiceValidator.validateForDgi(invoice);
                        if (!validationErrors.isEmpty()) {
                            Map<String, Object> failure = new HashMap<>();
                            failure.put("invoiceNumber", invoice.rn);
                            failure.put("errorCode", "INVOICE_VALIDATION_ERROR");
                            failure.put("errorDesc", String.join("; ", validationErrors));
                            failureResults.add(failure);
                            continue;
                        }
                        invoice.persist();
                        invoiceToProcess = invoice;
                    } else {
                        // Facture existante
                        if ("CONFIRMED".equals(existingInvoice.status)) {
                            Map<String, Object> failure = new HashMap<>();
                            failure.put("invoiceNumber", invoice.rn);
                            failure.put("error", "Facture déjà confirmée");
                            failure.put("uid", existingInvoice.uid);
                            failureResults.add(failure);
                            continue;
                        }
                        invoiceToProcess = existingInvoice;
                    }

                    // Soumission à la DGI
                    InvoiceEntity processedInvoice = dgiService.submitInvoice(invoiceToProcess, getDgiToken(entreprise));

                    // Vérifier le résultat
                    if ("CONFIRMED".equals(processedInvoice.status)) {
                        Map<String, Object> success = new HashMap<>();
                        success.put("invoiceNumber", processedInvoice.rn);
                        success.put("status", processedInvoice.status);
                        success.put("uid", processedInvoice.uid);
                        success.put("qrCode", processedInvoice.qrCode);
                        successResults.add(success);
                    } else {
                        Map<String, Object> failure = new HashMap<>();
                        failure.put("invoiceNumber", processedInvoice.rn);
                        failure.put("status", processedInvoice.status);
                        failure.put("uid", processedInvoice.uid);
                        failure.put("errorCode", processedInvoice.errorCode);
                        failure.put("errorDesc", processedInvoice.errorDesc);
                        failureResults.add(failure);
                    }

                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Erreur traitement facture " + invoice.rn + ": " + e.getMessage(), e);
                    Map<String, Object> failure = new HashMap<>();
                    failure.put("invoiceNumber", invoice.rn);
                    failure.put("error", "Exception: " + e.getMessage());
                    failureResults.add(failure);
                }
            }

            // 4. Construire la réponse consolidée
            Map<String, Object> batchResponse = new HashMap<>();
            batchResponse.put("totalSubmitted", invoices.size());
            batchResponse.put("totalSuccess", successResults.size());
            batchResponse.put("totalFailed", failureResults.size());
            batchResponse.put("successRate", String.format("%.2f%%",
                (successResults.size() * 100.0 / invoices.size())));
            batchResponse.put("success", successResults);
            batchResponse.put("failures", failureResults);
            batchResponse.put("message", successResults.size() + " factures traitées avec succès, "
                + failureResults.size() + " échecs");

            LOG.info("=== Traitement par lot terminé: " + successResults.size() + "/" + invoices.size() + " succès ===");

            return Response.ok(batchResponse).build();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Exception traitement lot: " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("BATCH_ERROR",
                            "Erreur interne lors du traitement par lot: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Récupère les détails d'une facture par son UID
     *
     * @param uid L'identifiant unique de la facture
     * @return La facture complète avec tous ses détails
     */
    @GET
    @Path("{uid}")
    @PermitAll
    @Operation(summary = "Récupérer les détails d'une facture par UID")
    public Response getInvoiceByUid(@PathParam("uid") String uid) {
        try {
            // Récupérer l'email depuis le token JWT
            String email = jwt.getClaim("email");
            if (requestContext == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("EMAIL_NOT_FOUND",
                                "Aucun email trouvé dans le token"))
                        .build();
            }

            // Récupérer l'entreprise
            Entreprise entreprise = getAuthenticatedEntreprise();
            if (entreprise == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("USER_NOT_FOUND",
                                "Aucun utilisateur trouvé pour cet email"))
                        .build();
            }

            // Récupérer la facture
            InvoiceEntity invoiceEntity = InvoiceEntity.find("nif = ?1 and uid = ?2", entreprise.nif, uid).firstResult();
            if (invoiceEntity == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("INVOICE_NOT_FOUND",
                                "Aucune facture trouvée avec ce UID"))
                        .build();
            }

            return Response.ok(
                    InvoiceEntityResponseMapper.toUserResponse(invoiceEntity)
            ).build();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Exception récupération facture: " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("INTERNAL_ERROR",
                            "Erreur interne: " + e.getMessage()))
                    .build();
        }
    }


    @POST
    @Path("/upload-excel")
    @RolesAllowed({"ADMIN", "USER", "admin"})
    @Transactional
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Importer un fichier Excel de factures et les soumettre à la DGI")
    public Response uploadExcelInvoices(byte[] data) {
        try {
            // Récupérer l'entreprise connectée
            String email = jwt.getClaim("email");
            Entreprise entreprise = Entreprise.find("email", email).firstResult();

            if (entreprise == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"Entreprise non trouvée\"}")
                    .build();
            }

            // Lire le fichier Excel
            Workbook workbook = openXlsxWorkbook(data);
            Sheet sheet = workbook.getSheetAt(0); // Première feuille

            Map<String, InvoiceEntity> invoiceByRn = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();

            // Parcourir les lignes (en sautant l'en-tête)
            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) {
                rowIterator.next(); // Sauter l'en-tête
            }

            int rowNum = 2; // Commence à la ligne 2 (après l'en-tête)
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                try {
                    // Valider la ligne avant création
                    String validationError = validateRow(row);
                    if (validationError != null) {
                        errors.add("Ligne " + rowNum + ": " + validationError);
                        rowNum++;
                        continue;
                    }

                    InvoiceEntity invoice = createInvoiceFromRow(row, entreprise);
                    applyOwnershipValidation(invoice, entreprise);

                    // Calculer les montants
                    mergeInvoiceLine(invoiceByRn, invoice);

                    // Persister la facture


                    // Envoyer à l'API DGI (asynchrone ou synchrone selon besoin)




                } catch (Exception e) {
                    errors.add("Ligne " + rowNum + ": " + e.getMessage());
                    e.printStackTrace(); // Pour le débogage
                }
                rowNum++;
            }

            workbook.close();
            List<InvoiceEntity> invoices = new ArrayList<>(invoiceByRn.values());
            int rejectedCount = 0;
            for (InvoiceEntity invoice : invoices) {
                if (isRejectedForNormalization(invoice)) {
                    rejectedCount++;
                    continue;
                }
                calculateInvoiceAmounts(invoice);
                invoice.persist();
                sendToDGINormalization(invoice, entreprise.dgiToken);
            }
            int successCount = invoices.size() - rejectedCount;

            // Préparer la réponse
            String responseMessage = String.format(
                "Import terminé. %d factures créées avec succès. %d erreurs.",
                successCount, errors.size()
            );

            if (!errors.isEmpty()) {
                return Response.status(Response.Status.PARTIAL_CONTENT)
                    .entity(new DgiUploadResponse(responseMessage, errors, invoices))
                    .build();
            }

            return Response.ok(new DgiUploadResponse(responseMessage, null, invoices)).build();

        } catch (Exception e) {
            if (isInvalidExcelException(e)) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("INVALID_FILE_TYPE", XLSX_REQUIRED_MESSAGE))
                    .build();
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"Erreur lors du traitement du fichier: " + e.getMessage() + "\"}")
                .build();
        }
    }

    /**
     * Soumet un fichier Excel de factures via un formulaire multipart
     *
     * @param file Le fichier Excel uploadé
     * @return Réponse contenant le fichier Excel mis à jour avec les résultats DGI
     */
    @POST
    @Path("/upload-file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"ADMIN", "USER"})
    @Transactional
    @Operation(summary = "Importer un fichier Excel de factures via formulaire multipart et les soumettre à la DGI")
    public Response uploadFile(@FormParam("file") FileUpload file) {
        try {
            // Validation du fichier
            if (file == null || file.filePath() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("FILE_REQUIRED", "Aucun fichier n'a été fourni"))
                    .build();
            }

            // Vérifier l'extension du fichier
            String fileName = file.fileName();
            if (!isXlsxFileName(fileName)) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("INVALID_FILE_TYPE", XLSX_REQUIRED_MESSAGE))
                    .build();
            }

            // Récupérer l'entreprise connectée
            String email = jwt.getClaim("email");
            Entreprise entreprise = Entreprise.find("email", email).firstResult();

            if (entreprise == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("ENTREPRISE_NOT_FOUND", "Entreprise non trouvée"))
                    .build();
            }

            // Lire le fichier Excel depuis le chemin temporaire
            byte[] data = java.nio.file.Files.readAllBytes(file.filePath());
            Workbook workbook = openXlsxWorkbook(data);
            Sheet sheet = workbook.getSheetAt(0); // Première feuille

            Map<String, InvoiceEntity> invoiceByRn = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();

            // Parcourir les lignes (en sautant l'en-tête)
            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) {
                rowIterator.next(); // Sauter l'en-tête
            }

            int rowNum = 2; // Commence à la ligne 2 (après l'en-tête)
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                try {
                    // Valider la ligne avant création
                    String validationError = validateRow(row);
                    if (validationError != null) {
                        errors.add("Ligne " + rowNum + ": " + validationError);
                        rowNum++;
                        continue;
                    }

                    InvoiceEntity invoice = createInvoiceFromRow(row, entreprise);
                    applyOwnershipValidation(invoice, entreprise);

                    // Calculer les montants
                    mergeInvoiceLine(invoiceByRn, invoice);

                    // Persister la facture


                    // Envoyer à l'API DGI (asynchrone ou synchrone selon besoin)




                } catch (Exception e) {
                    errors.add("Ligne " + rowNum + ": " + e.getMessage());
                    LOG.log(Level.WARNING, "Erreur traitement ligne " + rowNum, e);
                }
                rowNum++;
            }

            workbook.close();
            List<InvoiceEntity> invoices = new ArrayList<>(invoiceByRn.values());
            int rejectedCount = 0;
            for (InvoiceEntity invoice : invoices) {
                if (isRejectedForNormalization(invoice)) {
                    rejectedCount++;
                    continue;
                }
                calculateInvoiceAmounts(invoice);
                invoice.persist();
                sendToDGINormalization(invoice, entreprise.dgiToken);
            }
            int successCount = invoices.size() - rejectedCount;

            // Préparer la réponse
            String responseMessage = String.format(
                "Import terminé. %d factures créées avec succès. %d erreurs.",
                successCount, errors.size()
            );

            if (!errors.isEmpty()) {
                return Response.status(Response.Status.PARTIAL_CONTENT)
                    .entity(new DgiUploadResponse(responseMessage, errors, invoices))
                    .build();
            }

            return Response.ok(new DgiUploadResponse(responseMessage, null, invoices)).build();

        } catch (Exception e) {
            if (isInvalidExcelException(e)) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("INVALID_FILE_TYPE", XLSX_REQUIRED_MESSAGE))
                    .build();
            }
            LOG.log(Level.SEVERE, "Erreur lors du traitement du fichier uploadé", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("PROCESSING_ERROR", "Erreur lors du traitement du fichier: " + e.getMessage()))
                .build();
        }
    }

    private Workbook openXlsxWorkbook(byte[] data) throws java.io.IOException {
        if (data == null || data.length == 0) {
            throw new InvalidExcelFileException();
        }
        return new XSSFWorkbook(new ByteArrayInputStream(data));
    }

    private boolean isXlsxFileName(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".xlsx");
    }

    private boolean isInvalidExcelException(Exception e) {
        return e instanceof InvalidExcelFileException
            || e instanceof org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException
            || e instanceof org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException
            || e instanceof org.apache.poi.openxml4j.exceptions.InvalidFormatException
            || e instanceof org.apache.poi.ooxml.POIXMLException;
    }

    private static class InvalidExcelFileException extends RuntimeException {
    }

    private String validateRow(Row row) {
        int base = getInvoiceExcelBaseColumn(row);
        int commentOffset = hasExcelCommentColumns(row) ? EXCEL_COMMENT_COLUMN_COUNT : 0;
        int paymentOffset = hasExcelPaymentColumns(row) ? EXCEL_PAYMENT_COLUMN_COUNT : 0;

        if (isEmptyCell(row.getCell(base))) return "RN manquant";
        if (isEmptyCell(row.getCell(base + 1))) return "TYPE manquant - doit etre FV, EV, FT, FA, EA ou ET";
        if (isEmptyCell(row.getCell(base + 2))) return "CLIENT_NIF manquant";
        if (isEmptyCell(row.getCell(base + 3))) return "CLIENT_NAME manquant";
        if (isEmptyCell(row.getCell(base + 4))) return "CLIENT_TYPE manquant";
        if (isEmptyCell(row.getCell(base + 5))) return "ITEM_CODE manquant";
        if (isEmptyCell(row.getCell(base + 6))) return "ITEM_NAME manquant";
        if (isEmptyCell(row.getCell(base + 7))) return "ITEM_PRICE manquant";
        if (isEmptyCell(row.getCell(base + 8))) return "ITEM_QUANTITY manquante";
        if (isEmptyCell(row.getCell(base + 9))) return "ITEM_TAX_GROUP manquant";
        if (isEmptyCell(row.getCell(base + 10))) return "ITEM_ARTICLE_TYPE manquant - BIE, SER ou TAX";
        if (isEmptyCell(row.getCell(base + 12))) return "CURRENCY manquante";
        if (isEmptyCell(row.getCell(base + 16))) return "MODE manquant - ht ou ttc";

        String type = getStringCellValue(row.getCell(base + 1));
        if (type != null && !Arrays.asList("FV", "EV", "FT", "FA", "EA", "ET").contains(type.toUpperCase())) {
            return "TYPE invalide. Doit etre: FV, EV, FT, FA, EA ou ET";
        }

        String clientType = getStringCellValue(row.getCell(base + 4));
        List<String> validClientTypes = Arrays.asList("PE", "PM", "PC", "PL", "AO", "PP");
        if (clientType != null && !validClientTypes.contains(clientType.toUpperCase())) {
            return "CLIENT_TYPE invalide. Doit etre: PE, PM, PC, PL, AO ou PP";
        }

        String mode = getStringCellValue(row.getCell(base + 16));
        if (mode != null && !Arrays.asList("ht", "ttc").contains(mode.toLowerCase())) {
            return "MODE invalide. Doit etre: ht ou ttc";
        }

        String currency = getStringCellValue(row.getCell(base + 12));
        if (currency != null && currency.trim().length() > 3) {
            return "CURRENCY invalide. Utilisez un code de devise sur 3 caracteres, ex: CDF, USD, EUR";
        }

        String curCode = getStringCellValue(row.getCell(base + 20 + commentOffset));
        if (curCode != null && !curCode.isBlank() && curCode.trim().length() > 3) {
            return "CUR_CODE invalide. Utilisez un code de devise sur 3 caracteres, ex: CDF, USD, EUR";
        }

        String articleType = normalizeItemType(getStringCellValue(row.getCell(base + 10)));
        if (articleType == null) {
            return "ITEM_ARTICLE_TYPE invalide. Doit etre: BIEN/BIE, SERVICE/SER ou TAXE/TAX";
        }

        String taxGroup = getStringCellValue(row.getCell(base + 9));
        List<String> validTaxGroups = Arrays.asList("A", "B", "E", "X");
        if (taxGroup != null && !validTaxGroups.contains(taxGroup.toUpperCase())) {
            return "ITEM_TAX_GROUP invalide. Doit etre: A (19%), B (13%), E (7%) ou X (Exonere)";
        }

        Cell priceCell = row.getCell(base + 7);
        if (priceCell != null && priceCell.getCellType() == CellType.NUMERIC) {
            double price = priceCell.getNumericCellValue();
            if (price < 0) {
                return "ITEM_PRICE doit etre positif ou zero";
            }
        }

        Cell quantityCell = row.getCell(base + 8);
        if (quantityCell != null && quantityCell.getCellType() == CellType.NUMERIC) {
            double quantity = quantityCell.getNumericCellValue();
            if (quantity == 0) {
                return "ITEM_QUANTITY doit etre non-zero";
            }
        }

        Cell taxSpecCell = row.getCell(base + 14);
        if (taxSpecCell != null && taxSpecCell.getCellType() == CellType.NUMERIC) {
            double taxSpec = taxSpecCell.getNumericCellValue();
            if (taxSpec < 0) {
                return "SPECIFIC_TAX_AMOUNT doit etre positif ou zero";
            }
        }

        String taxSpecificValue = getStringCellValue(row.getCell(base + 15));
        if (taxSpecificValue != null && !taxSpecificValue.isBlank() && !isValidTaxSpecificValue(taxSpecificValue)) {
            return "TAX_SPECIFIC_VALUE invalide. Utilisez une valeur unitaire, ex: 100, ou un pourcentage, ex: 5%";
        }

        if (paymentOffset > 0) {
            String paymentName = getStringCellValue(row.getCell(base + 25 + commentOffset));
            List<String> validPaymentTypes = Arrays.asList("ESPECES", "VIREMENT", "CARTEBANCAIRE", "MOBILEMONEY", "CHEQUES", "CREDIT", "AUTRE");
            if (paymentName != null && !paymentName.isBlank() && !validPaymentTypes.contains(paymentName.trim().toUpperCase())) {
                return "PAYMENT_NAME invalide. Doit etre: ESPECES, VIREMENT, CARTEBANCAIRE, MOBILEMONEY, CHEQUES, CREDIT ou AUTRE";
            }
        }

        Cell curRateCell = row.getCell(base + 22 + commentOffset);
        if (curRateCell != null && curRateCell.getCellType() == CellType.NUMERIC) {
            double curRate = curRateCell.getNumericCellValue();
            if (curRate < 0) {
                return "CUR_RATE doit etre positif";
            }
        }

        return null;
    }
    private InvoiceEntity createInvoiceFromRow(Row row, Entreprise entreprise) {
        InvoiceEntity invoice = new InvoiceEntity();
        int base = getInvoiceExcelBaseColumn(row);
        int commentOffset = hasExcelCommentColumns(row) ? EXCEL_COMMENT_COLUMN_COUNT : 0;
        int paymentOffset = hasExcelPaymentColumns(row) ? EXCEL_PAYMENT_COLUMN_COUNT : 0;

        invoice.email = firstNonBlank(base > 0 ? getStringCellValue(row.getCell(0)) : null, entreprise.email);
        invoice.uid = null;
        invoice.nif = firstNonBlank(base > 0 ? getStringCellValue(row.getCell(base - 3)) : null, entreprise.nif);
        invoice.companyName = firstNonBlank(base > 0 ? getStringCellValue(row.getCell(base - 2)) : null, entreprise.nom);
        invoice.isf = firstNonBlank(base > 0 ? getStringCellValue(row.getCell(base - 1)) : null, entreprise.isf);

        invoice.rn = getStringCellValue(row.getCell(base));
        invoice.type = upperTrim(getStringCellValue(row.getCell(base + 1)));
        invoice.mode = lowerTrim(getStringCellValue(row.getCell(base + 16)));
        invoice.currency = upperTrim(getStringCellValue(row.getCell(base + 12)));

        invoice.client = new InvoiceEntity.Client();
        invoice.client.nif = getStringCellValue(row.getCell(base + 2));
        invoice.client.name = getStringCellValue(row.getCell(base + 3));
        invoice.client.type = upperTrim(getStringCellValue(row.getCell(base + 4)));
        invoice.client.typeDesc = getClientTypeDescription(invoice.client.type);

        InvoiceEntity.Item item = new InvoiceEntity.Item();
        item.code = getStringCellValue(row.getCell(base + 5));
        item.name = getStringCellValue(row.getCell(base + 6));
        item.type = normalizeItemType(getStringCellValue(row.getCell(base + 10)));
        item.price = getNumericCellValue(row.getCell(base + 7));
        item.quantity = getNumericCellValue(row.getCell(base + 8));
        item.unit = getStringCellValue(row.getCell(base + 13));
        item.taxGroup = upperTrim(getStringCellValue(row.getCell(base + 9)));
        item.taxSpecificAmount = getOptionalPositiveAmount(row.getCell(base + 14));
        item.taxSpecificValue = blankToNull(getStringCellValue(row.getCell(base + 15)));
        if (item.taxSpecificAmount == null && item.taxSpecificValue == null) {
            item.taxSpecificAmount = null;
            item.taxSpecificValue = null;
        }

        invoice.items = new ArrayList<>();
        invoice.items.add(item);

        if (commentOffset > 0) {
            invoice.cmta = getStringCellValue(row.getCell(base + 17));
            invoice.cmtb = getStringCellValue(row.getCell(base + 18));
            invoice.cmtc = getStringCellValue(row.getCell(base + 19));
            invoice.cmtd = getStringCellValue(row.getCell(base + 20));
            invoice.cmte = getStringCellValue(row.getCell(base + 21));
            invoice.cmtf = getStringCellValue(row.getCell(base + 22));
            invoice.cmtg = getStringCellValue(row.getCell(base + 23));
            invoice.cmth = getStringCellValue(row.getCell(base + 24));
        }

        invoice.reference = getStringCellValue(row.getCell(base + 17 + commentOffset));
        invoice.referenceType = getStringCellValue(row.getCell(base + 18 + commentOffset));
        invoice.referenceDesc = getStringCellValue(row.getCell(base + 19 + commentOffset));

        invoice.curCode = upperTrim(getStringCellValue(row.getCell(base + 20 + commentOffset)));
        String curDateStr = getStringCellValue(row.getCell(base + 21 + commentOffset));
        if (curDateStr != null && !curDateStr.isEmpty()) {
            try {
                invoice.curDate = parseDate(curDateStr);
            } catch (Exception e) {
                invoice.curDate = LocalDateTime.now();
            }
        }
        String curRateStr = getStringCellValue(row.getCell(base + 22 + commentOffset));
        if (curRateStr != null && !curRateStr.isEmpty()) {
            try {
                invoice.curRate = new BigDecimal(curRateStr);
            } catch (NumberFormatException e) {
                invoice.curRate = BigDecimal.ONE;
            }
        } else {
            invoice.curRate = BigDecimal.ONE;
        }

        invoice.issueDate = LocalDateTime.now();
        invoice.dueDate = LocalDateTime.now().plusDays(30);
        invoice.paymentDate = LocalDateTime.now().plusDays(7);
        invoice.validityDate = LocalDateTime.now().plusDays(30);
        invoice.createdAt = LocalDateTime.now();
        invoice.updatedAt = LocalDateTime.now();
        invoice.status = "PENDING";

        invoice.operator = new InvoiceEntity.Operator();
        invoice.operator.id = parseUuidOrDefault(
                paymentOffset > 0 ? getStringCellValue(row.getCell(base + 23 + commentOffset)) : null,
                entreprise.id
        );
        invoice.operator.name = firstNonBlank(
                paymentOffset > 0 ? getStringCellValue(row.getCell(base + 24 + commentOffset)) : null,
                entreprise.nom
        );

        InvoiceEntity.Payment payment = new InvoiceEntity.Payment();
        payment.name = upperTrim(firstNonBlank(
                paymentOffset > 0 ? getStringCellValue(row.getCell(base + 25 + commentOffset)) : null,
                "ESPECES"
        ));
        payment.amount = paymentOffset > 0 && !isEmptyCell(row.getCell(base + 26 + commentOffset))
                ? getNumericCellValue(row.getCell(base + 26 + commentOffset))
                : null;
        payment.currencyCode = upperTrim(paymentOffset > 0 ? getStringCellValue(row.getCell(base + 27 + commentOffset)) : null);
        payment.currencyRate = paymentOffset > 0 && !isEmptyCell(row.getCell(base + 28 + commentOffset))
                ? getNumericCellValue(row.getCell(base + 28 + commentOffset))
                : null;
        invoice.payments = new ArrayList<>();
        invoice.payments.add(payment);

        return invoice;
    }

    private void mergeInvoiceLine(Map<String, InvoiceEntity> invoiceByRn, InvoiceEntity invoiceLine) {
        if (invoiceLine == null || invoiceLine.rn == null || invoiceLine.rn.isBlank()) {
            return;
        }

        InvoiceEntity existing = invoiceByRn.get(invoiceLine.rn);
        if (existing == null) {
            invoiceByRn.put(invoiceLine.rn, invoiceLine);
            return;
        }

        if (invoiceLine.items != null && !invoiceLine.items.isEmpty()) {
            existing.items.addAll(invoiceLine.items);
        }

        if (isRejectedForNormalization(invoiceLine)) {
            existing.status = invoiceLine.status;
            existing.errorCode = invoiceLine.errorCode;
            existing.errorDesc = invoiceLine.errorDesc;
        }

        existing.cmta = firstNonBlank(existing.cmta, invoiceLine.cmta);
        existing.cmtb = firstNonBlank(existing.cmtb, invoiceLine.cmtb);
        existing.cmtc = firstNonBlank(existing.cmtc, invoiceLine.cmtc);
        existing.cmtd = firstNonBlank(existing.cmtd, invoiceLine.cmtd);
        existing.cmte = firstNonBlank(existing.cmte, invoiceLine.cmte);
        existing.cmtf = firstNonBlank(existing.cmtf, invoiceLine.cmtf);
        existing.cmtg = firstNonBlank(existing.cmtg, invoiceLine.cmtg);
        existing.cmth = firstNonBlank(existing.cmth, invoiceLine.cmth);
    }

    private void applyOwnershipValidation(InvoiceEntity invoice, Entreprise entreprise) {
        if (invoice == null || entreprise == null) {
            return;
        }

        boolean emailMismatch = hasText(invoice.email)
                && hasText(entreprise.email)
                && !invoice.email.trim().equalsIgnoreCase(entreprise.email.trim());
        boolean nifMismatch = hasText(invoice.nif)
                && hasText(entreprise.nif)
                && !invoice.nif.trim().equalsIgnoreCase(entreprise.nif.trim());

        if (emailMismatch || nifMismatch) {
            invoice.status = "REJECTED";
            invoice.errorCode = "OWNER_MISMATCH";
            invoice.errorDesc = "Facture non envoyee a la DGI: EMAIL/NIF du fichier ne correspondent pas a l'entreprise connectee";
        }
    }

    private boolean isRejectedForNormalization(InvoiceEntity invoice) {
        return invoice != null && "OWNER_MISMATCH".equals(invoice.errorCode);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int getInvoiceExcelBaseColumn(Row row) {
        Row header = row != null && row.getSheet() != null ? row.getSheet().getRow(0) : null;
        String firstHeader = header != null ? getStringCellValue(header.getCell(0)) : null;
        if (!"EMAIL".equalsIgnoreCase(firstHeader)) {
            return 0;
        }
        String secondHeader = getStringCellValue(header.getCell(1));
        return "UID".equalsIgnoreCase(secondHeader) ? EXCEL_ROOT_COLUMN_COUNT + 1 : EXCEL_ROOT_COLUMN_COUNT;
    }

    private boolean hasExcelCommentColumns(Row row) {
        Row header = row != null && row.getSheet() != null ? row.getSheet().getRow(0) : null;
        int base = getInvoiceExcelBaseColumn(row);
        String firstCommentHeader = header != null ? getStringCellValue(header.getCell(base + 17)) : null;
        return "CMTA".equalsIgnoreCase(firstCommentHeader);
    }

    private boolean hasExcelPaymentColumns(Row row) {
        Row header = row != null && row.getSheet() != null ? row.getSheet().getRow(0) : null;
        int base = getInvoiceExcelBaseColumn(row);
        int commentOffset = hasExcelCommentColumns(row) ? EXCEL_COMMENT_COLUMN_COUNT : 0;
        String firstPaymentHeader = header != null ? getStringCellValue(header.getCell(base + 23 + commentOffset)) : null;
        return "OPERATOR_ID".equalsIgnoreCase(firstPaymentHeader);
    }

    private String normalizeItemType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }

        return switch (rawType.trim().toUpperCase()) {
            case "BIEN", "BIE" -> "BIE";
            case "SERVICE", "SER" -> "SER";
            case "TAXE", "TAX" -> "TAX";
            default -> null;
        };
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private String upperTrim(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String lowerTrim(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private UUID parseUuidOrDefault(String value, UUID fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private boolean isValidTaxSpecificValue(String value) {
        String normalized = value == null ? "" : value.trim().replace(',', '.');
        return normalized.matches("\\d+(\\.\\d+)?%?") && !"%".equals(normalized);
    }

    private BigDecimal getOptionalPositiveAmount(Cell cell) {
        if (cell == null || isEmptyCell(cell)) {
            return null;
        }
        BigDecimal value = getNumericCellValue(cell);
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    private LocalDateTime parseDate(String dateStr) {
        try {
            // Essayer format DD/MM/YYYY
            String[] parts = dateStr.split("/");
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                return LocalDateTime.of(year, month, day, 0, 0, 0);
            }
        } catch (Exception e) {
            // Continuer
        }

        try {
            // Essayer format ISO YYYY-MM-DD
            return LocalDateTime.parse(dateStr + "T00:00:00");
        } catch (Exception e) {
            // Continuer
        }

        return LocalDateTime.now();
    }

    private String getClientTypeDescription(String clientType) {
            if (clientType == null) return "Personne Physique";

            return switch (clientType.toUpperCase()) {
                case "PE" -> "Personne Exploitant";
                case "PM" -> "Personne Morale";
                case "PC" -> "Professionnel Commerçant";
                case "PL" -> "Personne Libérale";
                case "AO" -> "Administration ou Organisme Public";
                case "PP" -> "Personne Physique";
                default -> "Personne Physique";
            };
        }

    private Entreprise getAuthenticatedEntreprise() {
        Object clientProperty = requestContext != null ? requestContext.getProperty("client") : null;
        if (clientProperty instanceof ApiClient apiClient) {
            if (apiClient.nif == null || apiClient.nif.isBlank()) {
                return null;
            }
            return Entreprise.find("nif", apiClient.nif).firstResult();
        }

        try {
            String email = jwt != null ? jwt.getClaim("email") : null;
            if (email != null && !email.isBlank()) {
                return Entreprise.find("email", email).firstResult();
            }
        } catch (Exception ignored) {
            // API-key requests do not carry JWT claims.
        }
        return null;
    }

    private String getDgiToken(Entreprise entreprise) {
        if (entreprise == null) {
            return null;
        }
        if (entreprise.dgiToken != null && !entreprise.dgiToken.isBlank()) {
            return entreprise.dgiToken;
        }
        return entreprise.token;
    }

    private boolean isEmptyCell(Cell cell) {
        if (cell == null) return true;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim().isEmpty();
            case BLANK:
                return true;
            default:
                return false;
        }
    }

    private void calculateInvoiceAmounts(InvoiceEntity invoice) {
        if (invoice.items == null || invoice.items.isEmpty()) {
            invoice.subtotal = BigDecimal.ZERO;
            invoice.total = BigDecimal.ZERO;
            invoice.curTotal = BigDecimal.ZERO;
            invoice.vtotal = BigDecimal.ZERO;
            return;
        }

        BigDecimal subtotal = BigDecimal.ZERO;

        for (InvoiceEntity.Item item : invoice.items) {
            if (item.price != null && item.quantity != null) {
                BigDecimal itemTotal = item.price.multiply(item.quantity);
                subtotal = subtotal.add(itemTotal);

                // Ajouter la taxe spécifique si présente
                if (item.taxSpecificAmount != null) {
                    subtotal = subtotal.add(item.taxSpecificAmount);
                }
            }
        }

        invoice.subtotal = subtotal;

        // Ajuster selon le taux de change si différent de 1
        if (invoice.curRate != null && invoice.curRate.compareTo(BigDecimal.ONE) != 0) {
            invoice.total = subtotal.multiply(invoice.curRate);
            invoice.curTotal = subtotal;
        } else {
            invoice.total = subtotal;
            invoice.curTotal = subtotal;
        }

        // Pour vtotal (peut être différent selon votre logique fiscale)
        invoice.vtotal = invoice.total;
    }

    private void sendToDGINormalization(InvoiceEntity invoice, String token) {
        try {
            // Créer le DTO pour l'API DGI
            DGIFactureDTO dgiFacture = mapToDGIDTO(invoice);

            DgiService dgiService = CDI.current().select(DgiService.class).get();
            dgiService.submitInvoice(invoice, token);

        } catch (Exception e) {
            invoice.status = "ERROR";
            invoice.errorDesc = "Exception lors de l'envoi à la DGI: " + e.getMessage();
            invoice.persist();
        }
    }

    private DGIFactureDTO mapToDGIDTO(InvoiceEntity invoice) {
        DGIFactureDTO dto = new DGIFactureDTO();

        // Mapping des champs obligatoires pour la DGI
        dto.nif = invoice.nif;
        dto.rn = invoice.rn;
        dto.type = invoice.type;
        dto.mode = invoice.mode;
        dto.isf = invoice.isf;
        dto.currency = invoice.currency;
        dto.subtotal = invoice.subtotal;
        dto.total = invoice.total;
        dto.issueDate = invoice.issueDate;
        dto.cmta = invoice.cmta;
        dto.cmtb = invoice.cmtb;
        dto.cmtc = invoice.cmtc;
        dto.cmtd = invoice.cmtd;
        dto.cmte = invoice.cmte;
        dto.cmtf = invoice.cmtf;
        dto.cmtg = invoice.cmtg;
        dto.cmth = invoice.cmth;

        // Nouveaux champs pour factures d'avoir
        dto.reference = invoice.reference;
        dto.referenceType = invoice.referenceType;
        dto.referenceDesc = invoice.referenceDesc;

        // Nouveaux champs pour devises
        dto.curCode = invoice.curCode;
        dto.curDate = invoice.curDate;
        dto.curRate = invoice.curRate;

        // Client
        dto.clientNif = invoice.client.nif;
        dto.clientName = invoice.client.name;
        dto.clientType = invoice.client.type;
        dto.clientTypeDesc = invoice.client.typeDesc;

        // Items
        dto.items = invoice.items.stream()
            .map(item -> {
                DGIItemDTO itemDTO = new DGIItemDTO();
                itemDTO.code = item.code;
                itemDTO.name = item.name;
                itemDTO.price = item.price;
                itemDTO.quantity = item.quantity;
                itemDTO.taxGroup = item.taxGroup;
                itemDTO.type = item.type;
                itemDTO.taxSpecificAmount = item.taxSpecificAmount;
                return itemDTO;
            })
            .collect(java.util.stream.Collectors.toList());

        return dto;
    }

    // Méthodes utilitaires pour lire les cellules Excel
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
                    // Formater pour éviter la notation scientifique
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

    private BigDecimal getNumericCellValue(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING:
                    String strValue = cell.getStringCellValue().trim();
                    if (strValue.isEmpty()) return BigDecimal.ZERO;
                    try {
                        return new BigDecimal(strValue);
                    } catch (NumberFormatException e) {
                        return BigDecimal.ZERO;
                    }
                case FORMULA:
                    switch (cell.getCachedFormulaResultType()) {
                        case NUMERIC:
                            return BigDecimal.valueOf(cell.getNumericCellValue());
                        case STRING:
                            String formulaValue = cell.getStringCellValue().trim();
                            if (formulaValue.isEmpty()) return BigDecimal.ZERO;
                            try {
                                return new BigDecimal(formulaValue);
                            } catch (NumberFormatException e) {
                                return BigDecimal.ZERO;
                            }
                        default:
                            return BigDecimal.ZERO;
                    }
                default:
                    return BigDecimal.ZERO;
            }
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // Classe pour le body multipart
    public static class MultipartBody {
        @FormParam("file")
        @PartType(MediaType.APPLICATION_OCTET_STREAM)
        public InputStream file;

        @FormParam("fileName")
        public String fileName;
    }

    // Classe de réponse
    public static class UploadResponse {
        public String message;
        public List<String> errors;
        public List<String> createdInvoiceNumbers;
        public int successCount;
        public int errorCount;

        public UploadResponse(String message, List<String> errors, List<String> createdInvoiceNumbers) {
            this.message = message;
            this.errors = errors;
            this.createdInvoiceNumbers = createdInvoiceNumbers;
            this.successCount = createdInvoiceNumbers != null ? createdInvoiceNumbers.size() : 0;
            this.errorCount = errors != null ? errors.size() : 0;
        }
    }


    public static class DgiUploadResponse {
        public String message;
        public List<String> errors;
        public List<String> createdInvoiceNumbers;
        public int successCount;
        public int errorCount;
        public List<DgiInvoiceResult> invoices;

        public DgiUploadResponse(String message, List<String> errors, List<InvoiceEntity> invoices) {
            this.message = message;
            this.errors = errors != null ? errors : List.of();
            this.invoices = invoices != null
                    ? invoices.stream().map(DgiInvoiceResult::new).toList()
                    : List.of();
            this.createdInvoiceNumbers = this.invoices.stream().map(result -> result.rn).toList();
            this.successCount = this.invoices.size();
            this.errorCount = this.errors.size();
        }
    }

    public static class DgiInvoiceResult {
        public String rn;
        public String status;
        public String uid;
        public BigDecimal total;
        public BigDecimal curTotal;
        public BigDecimal vtotal;
        public String errorCode;
        public String errorDesc;
        public String dateTime;
        public String qrCode;
        public String codeDEFDGI;
        public String counters;
        public String nim;

        public DgiInvoiceResult(InvoiceEntity invoice) {
            this.rn = invoice.rn;
            this.status = invoice.status;
            this.uid = invoice.uid;
            this.total = invoice.total;
            this.curTotal = invoice.curTotal;
            this.vtotal = invoice.vtotal;
            this.errorCode = invoice.errorCode;
            this.errorDesc = invoice.errorDesc;
            this.dateTime = invoice.dateTime;
            this.qrCode = invoice.qrCode;
            this.codeDEFDGI = invoice.codeDEFDGI;
            this.counters = invoice.counters;
            this.nim = invoice.nim;
        }
    }
    // DTOs pour l'API DGI (mis à jour avec les nouveaux champs)
    public static class DGIFactureDTO {
        public String nif;
        public String rn;
        public String type;
        public String mode;
        public String isf;
        public String currency;
        public BigDecimal subtotal;
        public BigDecimal total;
        public LocalDateTime issueDate;
        public String cmta;
        public String cmtb;
        public String cmtc;
        public String cmtd;
        public String cmte;
        public String cmtf;
        public String cmtg;
        public String cmth;

        // Nouveaux champs pour factures d'avoir
        public String reference;
        public String referenceType;
        public String referenceDesc;

        // Nouveaux champs pour devises
        public String curCode;
        public LocalDateTime curDate;
        public BigDecimal curRate;

        // Client
        public String clientNif;
        public String clientName;
        public String clientType;
        public String clientTypeDesc;

        // Items
        public List<DGIItemDTO> items;
    }

    public static class DGIItemDTO {
        public String code;
        public String name;
        public BigDecimal price;
        public BigDecimal quantity;
        public String taxGroup;
        public String type;
        public BigDecimal taxSpecificAmount;
    }
    // Méthode pour vérifier si une cellule contient une date
    private boolean isDateCell(Cell cell) {
        if (cell == null) return false;

        try {
            // Vérifier le format de la cellule
            CellStyle style = cell.getCellStyle();
            String format = style.getDataFormatString();

            // Les formats de date communs dans Excel
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

    // Méthode pour formater les valeurs numériques
    private String formatNumericValue(double value) {
        // Pour éviter les .0 pour les nombres entiers
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

}
