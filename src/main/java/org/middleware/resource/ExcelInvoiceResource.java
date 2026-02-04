package org.middleware.resource;

import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.middleware.dto.ApiResponse;
import org.middleware.models.Entreprise;
import org.middleware.service.ExcelInvoiceService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Resource REST pour le traitement des fichiers Excel de factures.
 * 
 * Endpoints:
 * - POST /api/excel/process : Traite un fichier Excel et retourne le fichier enrichi
 * - GET /api/excel/template : Télécharge un template Excel vide
 */
@Path("/api/excel")
@Tag(name = "Excel Invoices", description = "Traitement par lot de factures via fichiers Excel")
public class ExcelInvoiceResource {

    private static final Logger LOG = Logger.getLogger(ExcelInvoiceResource.class.getName());

    @Inject
    ExcelInvoiceService excelService;

    @Inject
    JsonWebToken jwt;

    /**
     * Traite un fichier Excel de factures et retourne le fichier enrichi.
     * 
     * Le fichier d'entrée doit contenir les colonnes:
     * rn, type, clientNif, clientName, itemCode, itemName, itemPrice, itemQuantity, itemTaxGroup, currency, mode
     * 
     * Le fichier de sortie contiendra ces colonnes + nif, isf, companyName, subtotal, total, status, errorMessage
     */
    @POST
    @Path("/process")
    @RolesAllowed({"ADMIN", "USER"})
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(
        summary = "Traiter un fichier Excel de factures",
        description = "Upload un fichier Excel, normalise les données, ajoute les informations de l'entreprise et retourne le fichier enrichi"
    )
    @APIResponse(
        responseCode = "200",
        description = "Fichier Excel traité avec succès",
        content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    )
    @APIResponse(
        responseCode = "400",
        description = "Fichier invalide ou erreur de traitement"
    )
    @APIResponse(
        responseCode = "401",
        description = "Non autorisé - Token JWT manquant ou invalide"
    )
    public Response processExcelFile(InputStream fileInputStream) {
        try {
            LOG.info("=== Réception d'un fichier Excel à traiter ===");
            
            // 1. Récupérer l'utilisateur depuis le JWT
            String email = jwt.getClaim("email");
            if (email == null || email.isEmpty()) {
                LOG.warning("Email non trouvé dans le token JWT");
                return Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(ApiResponse.error("EMAIL_NOT_FOUND", "Aucun email trouvé dans le token"))
                        .build();
            }

            // 2. Récupérer l'entreprise de l'utilisateur
            Entreprise entreprise = Entreprise.find("email", email).firstResult();
            if (entreprise == null) {
                LOG.warning("Entreprise non trouvée pour email: " + email);
                return Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(ApiResponse.error("USER_NOT_FOUND", "Aucune entreprise trouvée pour cet email"))
                        .build();
            }

            LOG.info("Traitement pour entreprise: " + entreprise.nom + " (NIF: " + entreprise.nif + ")");

            // 3. Traiter le fichier Excel
            byte[] processedFile = excelService.processExcelFile(fileInputStream, entreprise);

            // 4. Retourner le fichier traité
            return Response.ok(processedFile)
                    .header("Content-Disposition", "attachment; filename=\"factures_traitees.xlsx\"")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .build();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erreur lors du traitement Excel: " + e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error("PROCESSING_ERROR", "Erreur lors du traitement du fichier: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Endpoint alternatif avec multipart form-data pour upload de fichier
     */
    @POST
    @Path("/upload")
    @RolesAllowed({"ADMIN", "USER"})
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(
        summary = "Upload et traiter un fichier Excel (multipart)",
        description = "Upload un fichier Excel via form-data, normalise les données et retourne le fichier enrichi"
    )
    public Response uploadAndProcessExcel(@FormParam("file") InputStream fileInputStream,
                                          @FormParam("fileName") String fileName) {
        LOG.info("Upload multipart reçu: " + (fileName != null ? fileName : "fichier sans nom"));
        return processExcelFile(fileInputStream);
    }

    /**
     * Télécharge un template Excel vide avec les en-têtes et les instructions.
     */
    @GET
    @Path("/template")
    @RolesAllowed({"ADMIN", "USER"})
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(
        summary = "Télécharger le template Excel",
        description = "Télécharge un fichier Excel template avec les colonnes requises et une ligne d'exemple"
    )
    @APIResponse(
        responseCode = "200",
        description = "Template Excel généré",
        content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    )
    public Response downloadTemplate() {
        try {
            LOG.info("Génération du template Excel");
            
            byte[] template = excelService.generateTemplate();
            
            return Response.ok(template)
                    .header("Content-Disposition", "attachment; filename=\"template_factures.xlsx\"")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .build();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erreur lors de la génération du template: " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiResponse.error("TEMPLATE_ERROR", "Erreur lors de la génération du template"))
                    .build();
        }
    }

    /**
     * Retourne les informations sur le format attendu du fichier Excel.
     */
    @GET
    @Path("/info")
    @RolesAllowed({"ADMIN", "USER"})
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
        summary = "Informations sur le format Excel",
        description = "Retourne la description des colonnes attendues dans le fichier Excel"
    )
    public Response getExcelInfo() {
        return Response.ok(new ExcelFormatInfo()).build();
    }

    /**
     * DTO pour les informations du format Excel
     */
    public static class ExcelFormatInfo {
        public String description = "Format de fichier Excel pour l'import de factures";
        public ColumnInfo[] inputColumns = {
            new ColumnInfo("rn", "Numéro de référence de la facture", true, "Texte unique"),
            new ColumnInfo("type", "Type de facture", false, "FN (Normal), FA (Avoir), FP (Proforma)"),
            new ColumnInfo("clientNif", "NIF du client", false, "Format: A1234567K"),
            new ColumnInfo("clientName", "Nom du client", false, "Texte"),
            new ColumnInfo("itemCode", "Code de l'article", false, "Texte"),
            new ColumnInfo("itemName", "Nom de l'article", false, "Texte"),
            new ColumnInfo("itemPrice", "Prix unitaire", true, "Nombre décimal"),
            new ColumnInfo("itemQuantity", "Quantité", true, "Nombre décimal"),
            new ColumnInfo("itemTaxGroup", "Groupe de TVA", false, "A (16%), B (8%), C (0%), D (Exonéré)"),
            new ColumnInfo("currency", "Devise", false, "CDF, USD"),
            new ColumnInfo("mode", "Mode de facturation", false, "0 (normal), 1 (spécial)")
        };
        public String[] outputColumnsAdded = {
            "nif - NIF de l'entreprise (auto)",
            "isf - ISF de l'entreprise (auto)",
            "companyName - Nom de l'entreprise (auto)",
            "subtotal - Sous-total calculé (prix × quantité)",
            "total - Total avec TVA",
            "status - VALID, INVALID, ou DUPLICATE",
            "errorMessage - Message d'erreur si invalide"
        };
    }

    public static class ColumnInfo {
        public String name;
        public String description;
        public boolean required;
        public String format;

        public ColumnInfo(String name, String description, boolean required, String format) {
            this.name = name;
            this.description = description;
            this.required = required;
            this.format = format;
        }
    }
}
