package org.middleware.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.middleware.dto.ApiResponse;
import org.middleware.models.Entreprise;
import org.middleware.models.InvoiceEntity;
import org.middleware.service.DgiService;
import org.middleware.service.InvoiceEntityResponseMapper;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/api/invoice")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InvoiceResource {

    private static final Logger LOG = Logger.getLogger(InvoiceResource.class.getName());

    @Context
    ContainerRequestContext requestContext;

    @Inject
    JsonWebToken jwt;

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
}
