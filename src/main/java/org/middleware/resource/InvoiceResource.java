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
import org.middleware.dto.ApiResponse;
import org.middleware.models.Entreprise;
import org.middleware.models.InvoiceEntity;
import org.middleware.service.DgiService;
import org.middleware.service.ExcelTraitement;
import org.middleware.service.InvoiceEntityResponseMapper;

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

    @Context
    ContainerRequestContext requestContext;

    @Inject
    JsonWebToken jwt;

    @Inject
    ExcelTraitement excelTraitement;

    @GET
    @Path("test")
    @RolesAllowed({"ADMIN","USER"})
    public String test(){
        return "Ok";
    }

    @GET
    @Path("/debug")
    @RolesAllowed({"USER", "ADMIN"})
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
    @RolesAllowed({"ADMIN","USER"})
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
            String email = jwt.getClaim("email");
            if (email == null || email.isEmpty()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("EMAIL_NOT_FOUND",
                                "Aucun email trouvé dans le token"))
                        .build();
            }

            Entreprise entreprise = Entreprise.find("email", email).firstResult();
            if (entreprise == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("USER_NOT_FOUND",
                                "Aucun utilisateur trouvé pour cet email"))
                        .build();
            }

            // 3. Vérifier si la facture existe déjà
            InvoiceEntity existingInvoice = InvoiceEntity.find("rn", invoice.rn).firstResult();

            InvoiceEntity invoiceToProcess;
            if (existingInvoice == null) {
                // Nouvelle facture
                LOG.info("Nouvelle facture: " + invoice.rn);
                invoice.id = null;
                invoice.email = email;
                invoice.nif = entreprise.nif;
                invoice.isf = entreprise.isf;
                invoice.status = "PENDING";
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
            InvoiceEntity processedInvoice = dgiService.submitInvoice(invoiceToProcess, entreprise.token);

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
    @RolesAllowed({"ADMIN","USER"})
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
            String email = jwt.getClaim("email");
            if (email == null || email.isEmpty()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("EMAIL_NOT_FOUND",
                                "Aucun email trouvé dans le token"))
                        .build();
            }

            Entreprise entreprise = Entreprise.find("email", email).firstResult();
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

                    // Vérifier si la facture existe déjà
                    InvoiceEntity existingInvoice = InvoiceEntity.find("rn", invoice.rn).firstResult();

                    InvoiceEntity invoiceToProcess;
                    if (existingInvoice == null) {
                        // Nouvelle facture
                        invoice.id = null;
                        invoice.email = email;
                        invoice.nif = entreprise.nif;
                        invoice.isf = entreprise.isf;
                        invoice.status = "PENDING";
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
                    InvoiceEntity processedInvoice = dgiService.submitInvoice(invoiceToProcess, entreprise.token);

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
    @RolesAllowed({"ADMIN","USER"})
    @Operation(summary = "Récupérer les détails d'une facture par UID")
    public Response getInvoiceByUid(@PathParam("uid") String uid) {
        try {
            // Récupérer l'email depuis le token JWT
            String email = jwt.getClaim("email");
            if (email == null || email.isEmpty()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("EMAIL_NOT_FOUND",
                                "Aucun email trouvé dans le token"))
                        .build();
            }

            // Récupérer l'entreprise
            Entreprise entreprise = Entreprise.find("email", email).firstResult();
            if (entreprise == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("USER_NOT_FOUND",
                                "Aucun utilisateur trouvé pour cet email"))
                        .build();
            }

            // Récupérer la facture
            InvoiceEntity invoiceEntity = InvoiceEntity.find("email = ?1 and uid = ?2", email, uid).firstResult();
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
    @RolesAllowed({"ADMIN", "USER"})
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
            InputStream fileInputStream = new ByteArrayInputStream(data);
            Workbook workbook = new XSSFWorkbook(fileInputStream);
            Sheet sheet = workbook.getSheetAt(0); // Première feuille
            
            List<InvoiceEntity> invoices = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int successCount = 0;
            
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
                    
                    // Calculer les montants
                    calculateInvoiceAmounts(invoice);
                    
                    // Persister la facture
                    invoice.persist();
                    
                    // Envoyer à l'API DGI (asynchrone ou synchrone selon besoin)
                    sendToDGINormalization(invoice, entreprise.token);
                    
                    invoices.add(invoice);
                    successCount++;
                    
                } catch (Exception e) {
                    errors.add("Ligne " + rowNum + ": " + e.getMessage());
                    e.printStackTrace(); // Pour le débogage
                }
                rowNum++;
            }
            
            workbook.close();
            fileInputStream.close();
            
            // Préparer la réponse
            String responseMessage = String.format(
                "Import terminé. %d factures créées avec succès. %d erreurs.",
                successCount, errors.size()
            );
            
            if (!errors.isEmpty()) {
                return Response.status(Response.Status.PARTIAL_CONTENT)
                    .entity(new UploadResponse(responseMessage, errors, invoices.stream()
                        .map(inv -> inv.rn)
                        .toList()))
                    .build();
            }

            // Mettre à jour le fichier Excel
        byte[] updatedExcel = excelTraitement.updateExcelFromInvoiceEntities(invoices, data);
        
        // Retourner le fichier mis à jour
        return Response.ok(updatedExcel)
                .header("Content-Disposition", "attachment; filename=\"factures_mise_a_jour.xlsx\"")
                .type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .build();
            
            // return Response.ok(new UploadResponse(responseMessage, null, invoices.stream()
            //         .map(inv -> inv.rn)
            //         .toList()))
            //     .build();
            
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"error\": \"Erreur lors du traitement du fichier: " + e.getMessage() + "\"}")
                .build();
        }
    }

    private String validateRow(Row row) {
        // Colonnes selon votre fichier Excel avec les nouvelles colonnes:
        // A(0): rn, B(1): type, C(2): clientNif, D(3): clientName, E(4): clientType, 
        // F(5): itemCode, G(6): itemName, H(7): itemPrice, I(8): itemQuantity, 
        // J(9): itemTaxGroup, K(10): itemArticleType, L(11): unitPriceMode, 
        // M(12): currency, N(13): unit, O(14): specificTaxAmount, P(15): mode,
        // Q(16): reference, R(17): referenceType, S(18): referenceDesc,
        // T(19): curCode, U(20): curDate, V(21): curRate
        
        // Validation des champs obligatoires
        if (isEmptyCell(row.getCell(0))) return "RN manquant";
        if (isEmptyCell(row.getCell(1))) return "Type de facture manquant";
        if (isEmptyCell(row.getCell(2))) return "NIF client manquant";
        if (isEmptyCell(row.getCell(3))) return "Nom client manquant";
        if (isEmptyCell(row.getCell(4))) return "Type de client manquant";
        if (isEmptyCell(row.getCell(5))) return "Code article manquant";
        if (isEmptyCell(row.getCell(6))) return "Nom article manquant";
        if (isEmptyCell(row.getCell(7))) return "Prix manquant";
        if (isEmptyCell(row.getCell(8))) return "Quantité manquante";
        if (isEmptyCell(row.getCell(9))) return "Groupe de taxe manquant";
        if (isEmptyCell(row.getCell(10))) return "Type d'article manquant";
        if (isEmptyCell(row.getCell(11))) return "Mode de prix manquant";
        if (isEmptyCell(row.getCell(12))) return "Devise manquante";
        
        // Valider le client selon votre fichier Excel
        String clientType = getStringCellValue(row.getCell(4)); // Colonne E: clientType
        String clientNif = getStringCellValue(row.getCell(2)); // Colonne C: clientNif
        String clientName = getStringCellValue(row.getCell(3)); // Colonne D: clientName
        
        // Valider le type de client
        if (clientType == null || clientType.trim().isEmpty()) {
            return "Type de client manquant";
        }
        
        // Valider les types de client autorisés
        List<String> validClientTypes = Arrays.asList("PP", "PM", "PC", "PL", "AO");
        String normalizedClientType = clientType.toUpperCase();
        if (!validClientTypes.contains(normalizedClientType)) {
            return "Type de client invalide. Doit être: PP, PM, PC, PL ou AO";
        }
        
        // Valider le NIF selon le type de client
        if (!"PP".equalsIgnoreCase(clientType)) {
            // Pour tous les types sauf PP, le NIF est obligatoire
            if (clientNif == null || clientNif.trim().isEmpty()) {
                return "NIF client obligatoire pour le type " + clientType;
            }
            
            // Valider le format du NIF (exemple: commencer par NIF)
            if (!clientNif.startsWith("NIF")) {
                return "Format NIF invalide. Doit commencer par 'NIF'";
            }
        } else {
            // Pour PP, le NIF peut être null ou vide
            // Mais on valide le nom client
            if (clientName == null || clientName.trim().isEmpty()) {
                return "Nom client obligatoire pour les clients PP";
            }
        }
        
        // Valider les valeurs numériques
        Cell priceCell = row.getCell(7);
        if (priceCell != null && priceCell.getCellType() == CellType.NUMERIC) {
            double price = priceCell.getNumericCellValue();
            if (price <= 0) {
                return "Le prix doit être supérieur à 0";
            }
        }
        
        Cell quantityCell = row.getCell(8);
        if (quantityCell != null && quantityCell.getCellType() == CellType.NUMERIC) {
            double quantity = quantityCell.getNumericCellValue();
            if (quantity <= 0) {
                return "La quantité doit être supérieure à 0";
            }
        }
        
        // Valider les informations de devise si fournies
        String curCode = getStringCellValue(row.getCell(19)); // Colonne T: curCode
        String curDateStr = getStringCellValue(row.getCell(20)); // Colonne U: curDate
        String curRateStr = getStringCellValue(row.getCell(21)); // Colonne V: curRate
        
        if (curCode != null && !curCode.trim().isEmpty()) {
            // Si le code devise est fourni, valider la date et le taux
            if (curDateStr == null || curDateStr.trim().isEmpty()) {
                return "Date de devise manquante quand le code devise est fourni";
            }
            
            if (curRateStr == null || curRateStr.trim().isEmpty()) {
                return "Taux de change manquant quand le code devise est fourni";
            }
            
            try {
                BigDecimal curRate = new BigDecimal(curRateStr);
                if (curRate.compareTo(BigDecimal.ZERO) <= 0) {
                    return "Le taux de change doit être supérieur à 0";
                }
            } catch (NumberFormatException e) {
                return "Format de taux de change invalide";
            }
        }
        
        return null; // Pas d'erreur
    }

    private InvoiceEntity createInvoiceFromRow(Row row, Entreprise entreprise) {
        InvoiceEntity invoice = new InvoiceEntity();
        
        // Récupérer l'entreprise via le token
        invoice.email = entreprise.email;
        invoice.nif = entreprise.nif;
        invoice.companyName = entreprise.nom;
        invoice.isf = entreprise.isf;
        
        // Informations de base depuis Excel avec les bons indices
        // Note: les indices ont changé à cause de l'ajout de la colonne taxSpecificValue
        // A(0): rn, B(1): type, C(2): clientNif, D(3): clientName, E(4): clientType, 
        // F(5): itemCode, G(6): itemName, H(7): itemPrice, I(8): itemQuantity, 
        // J(9): itemTaxGroup, K(10): itemArticleType, L(11): unitPriceMode, 
        // M(12): currency, N(13): unit, O(14): specificTaxAmount, P(15): taxSpecificValue,
        // Q(16): mode, R(17): reference, S(18): referenceType, T(19): referenceDesc,
        // U(20): curCode, V(21): curDate, W(22): curRate
        
        invoice.rn = getStringCellValue(row.getCell(0)); // Colonne A: rn
        invoice.type = getStringCellValue(row.getCell(1)); // Colonne B: type (FV)
        
        // Client
        invoice.client = new InvoiceEntity.Client();
        invoice.client.nif = getStringCellValue(row.getCell(2)); // Colonne C: clientNif
        invoice.client.name = getStringCellValue(row.getCell(3)); // Colonne D: clientName
        invoice.client.type = getStringCellValue(row.getCell(4)); // Colonne E: clientType
        
        // Déterminer la description du type de client
        invoice.client.typeDesc = getClientTypeDescription(invoice.client.type);
        
        // Items
        InvoiceEntity.Item item = new InvoiceEntity.Item();
        item.code = getStringCellValue(row.getCell(5)); // Colonne F: itemCode
        item.name = getStringCellValue(row.getCell(6)); // Colonne G: itemName
        item.price = getNumericCellValue(row.getCell(7)); // Colonne H: itemPrice
        item.quantity = getNumericCellValue(row.getCell(8)); // Colonne I: itemQuantity
        item.taxGroup = getStringCellValue(row.getCell(9)); // Colonne J: itemTaxGroup
        
        // Type d'article (SER ou BIE)
        String articleType = getStringCellValue(row.getCell(10)); // Colonne K: itemArticleType
        item.type = "BIE".equals(articleType) ? "BIE" : "SER"; // B pour Bien, S pour Service
        
        // Mode de prix et devise
        String unitPriceMode = getStringCellValue(row.getCell(11)); // Colonne L: unitPriceMode
        //invoice.mode = "ht".equals(unitPriceMode) ? "HT" : "TTC";
        
        invoice.currency = getStringCellValue(row.getCell(12)); // Colonne M: currency
        
        // Unit (colonne N) - non utilisé dans InvoiceEntity pour l'instant
        String unit = getStringCellValue(row.getCell(13)); // Colonne N: unit
        
        // Taxe spécifique - LES INDICES ONT CHANGÉ !
        BigDecimal taxSpecificAmount = getNumericCellValue(row.getCell(14)); // Colonne O: specificTaxAmount
        if(taxSpecificAmount != null && taxSpecificAmount.compareTo(BigDecimal.ZERO) > 0) {
            item.taxSpecificAmount = taxSpecificAmount;
        }
        // = getNumericCellValue(row.getCell(14)); // Colonne O: specificTaxAmount
        
        // NOUVELLE COLONNE: taxSpecificValue (colonne P)
        // Ce champ est requis lorsque taxSpecificAmount est spécifié 
        String taxSpecificValue = getStringCellValue(row.getCell(15)); // Colonne P: taxSpecificValue
        if (taxSpecificValue != null && !taxSpecificValue.trim().isEmpty()) {
            try {
                // Stocker comme BigDecimal ou String selon votre modèle
                // Si votre Item a un champ taxSpecificValue, utilisez-le
                // Sinon, vous pouvez stocker dans un champ additionnel
                item.taxSpecificValue = taxSpecificValue; // Assurez-vous que votre Item a ce champ
            } catch (Exception e) {
                // Gérer l'erreur de parsing si nécessaire
            }
        }
        
        // Mode final (colonne Q - maintenant à l'index 16)
        String modeValue = getStringCellValue(row.getCell(16)); // Colonne Q: mode
        if (modeValue != null) {
            if (modeValue.equals("0") || modeValue.equals("1")) {
                invoice.mode = modeValue.equals("0") ? "ht" : "ttc";
            } else if (modeValue.equalsIgnoreCase("ht") || modeValue.equalsIgnoreCase("ttc")) {
                invoice.mode = modeValue.toLowerCase();
            }
        }
        
        // === CHAMPS POUR FACTURES D'AVOIR ===
        // Les indices ont changé à cause de l'ajout de taxSpecificValue
        invoice.reference = getStringCellValue(row.getCell(17)); // Colonne R: reference (était Q)
        invoice.referenceType = getStringCellValue(row.getCell(18)); // Colonne S: referenceType (était R)
        invoice.referenceDesc = getStringCellValue(row.getCell(19)); // Colonne T: referenceDesc (était S)
        
        // === CHAMPS POUR DEVISES ===
        // Les indices ont changé à cause de l'ajout de taxSpecificValue
        invoice.curCode = getStringCellValue(row.getCell(20)); // Colonne U: curCode (était T)
        
        // Gestion de la date de devise
        String curDateStr = getStringCellValue(row.getCell(21)); // Colonne V: curDate (était U)
        if (curDateStr != null && !curDateStr.trim().isEmpty()) {
            try {
                // Essayer de parser comme LocalDateTime
                invoice.curDate = LocalDateTime.parse(curDateStr);
            } catch (Exception e) {
                try {
                    // Essayer de parser comme LocalDate et convertir
                    LocalDate date = LocalDate.parse(curDateStr);
                    invoice.curDate = date.atStartOfDay();
                } catch (Exception e2) {
                    // Si c'est une cellule date Excel
                    Cell curDateCell = row.getCell(21);
                    if (curDateCell != null && curDateCell.getCellType() == CellType.NUMERIC && isDateCell(curDateCell)) {
                        invoice.curDate = curDateCell.getLocalDateTimeCellValue();
                    } else {
                        // Date par défaut
                        invoice.curDate = LocalDateTime.now();
                    }
                }
            }
        }
        
        // Taux de change (colonne W - maintenant à l'index 22)
        String curRateStr = getStringCellValue(row.getCell(22)); // Colonne W: curRate (était V)
        if (curRateStr != null && !curRateStr.trim().isEmpty()) {
            try {
                invoice.curRate = new BigDecimal(curRateStr);
            } catch (NumberFormatException e) {
                invoice.curRate = BigDecimal.ONE; // Valeur par défaut
            }
        } else {
            invoice.curRate = BigDecimal.ONE; // Valeur par défaut
        }
        
        // Initialiser la liste d'items
        invoice.items = new ArrayList<>();
        invoice.items.add(item);
        
        // Dates par défaut
        invoice.issueDate = LocalDateTime.now();
        invoice.dueDate = LocalDateTime.now().plusDays(30);
        invoice.createdAt = LocalDateTime.now();
        invoice.updatedAt = LocalDateTime.now();
        
        // Statut
        invoice.status = "PENDING";
        
        // Operator (celui qui a créé la facture)
        invoice.operator = new InvoiceEntity.Operator();
        invoice.operator.id = entreprise.id;
        invoice.operator.name = entreprise.nom;
        
        // UID unique
        invoice.uid = UUID.randomUUID().toString();
        
        return invoice;
    }

    private String getClientTypeDescription(String clientType) {
            if (clientType == null) return "Personne Physique";
            
            return switch (clientType.toUpperCase()) {
                case "PP" -> "Personne Physique";
                case "PM" -> "Personne Morale";
                case "PC" -> "Professionnel Commerçant";
                case "PL" -> "Personne Libérale";
                case "AO" -> "Administration ou Organisme Public";
                default -> "Personne Physique";
            };
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
