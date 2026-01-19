package org.middleware.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.middleware.dto.DgiResponse;
import org.middleware.models.InvoiceEntity;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class DgiService {

    private static final Logger LOG = Logger.getLogger(DgiService.class.getName());
    private static final String DGI_API_BASE_URL = "https://developper.dgirdc.cd/edef/api/invoice";
    private static final int DGI_TIMEOUT_SECONDS = 30;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public DgiService() {
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Soumet une facture à la DGI (ÉTAPE 1)
     * Mise à jour et sauvegarde l'InvoiceEntity avec le statut et les données
     * 
     * @param invoice La facture à soumettre
     * @param dgiToken Le token d'authentification DGI
     * @return InvoiceEntity mise à jour avec:
     *         - status = "PHASE1" en succès
     *         - status = "PENDING" + errorCode/errorDesc en cas d'erreur
     *         - uid, total, curTotal, vtotal si succès
     */
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    public InvoiceEntity submitInvoicePhase1(InvoiceEntity invoice, String dgiToken) {
        try {
            LOG.info("=== PHASE 1: Soumission de la facture RN=" + invoice.rn + " ===");
            
            // Vérification que la facture n'est pas déjà soumise
            if ("PHASE1".equals(invoice.status) || "CONFIRMED".equals(invoice.status)) {
                LOG.warning("Facture déjà soumise: " + invoice.rn + " (Statut: " + invoice.status + ")");
                invoice.errorCode = "INVOICE_ALREADY_SUBMITTED";
                invoice.errorDesc = "Cette facture a déjà été soumise à la DGI";
                invoice.persist();
                return invoice;
            }
            
            // Étape 1: Soumission de la facture
            JsonNode submissionResponse = submitInvoiceToDgi(invoice, dgiToken);
            
            if (hasError(submissionResponse)) {
                LOG.warning("Erreur lors de la soumission Phase 1: " + submissionResponse.toString());
                invoice.errorCode = extractField(submissionResponse, "errorCode");
                invoice.errorDesc = extractField(submissionResponse, "errorDesc");
                invoice.persist();
                return invoice;
            }
            
            // Extraction et mise à jour des données de soumission
            invoice.uid = extractField(submissionResponse, "uid");
            invoice.total = extractBigDecimal(submissionResponse, "total");
            invoice.curTotal = extractBigDecimal(submissionResponse, "curTotal");
            invoice.vtotal = extractBigDecimal(submissionResponse, "vtotal");
            invoice.status = "PHASE1";
            invoice.errorCode = null;
            invoice.errorDesc = null;
            
            // Sauvegarde de l'entité
            invoice.persist();
            
            LOG.info("✓ PHASE 1 complétée avec succès - UID: " + invoice.uid);
            
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Exception PHASE 1: " + e.getMessage(), e);
            invoice.status = "PENDING";
            invoice.errorCode = "DGI_PHASE1_ERROR";
            invoice.errorDesc = "Erreur lors de la soumission: " + e.getMessage();
            invoice.persist();
        }
        
        return invoice;
    }

    /**
     * Confirme la soumission d'une facture à la DGI (ÉTAPE 2)
     * Mise à jour et sauvegarde l'InvoiceEntity avec les données de confirmation
     * 
     * @param invoice La facture à confirmer (doit avoir un UID de PHASE 1)
     * @param dgiToken Le token d'authentification DGI
     * @return InvoiceEntity mise à jour avec:
     *         - status = "CONFIRMED" en succès
     *         - status = "PHASE1" + errorCode/errorDesc en cas d'erreur
     *         - qrCode, dateTime, codeDEFDGI, nim si succès
     */
    @Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
    public InvoiceEntity confirmInvoicePhase2(InvoiceEntity invoice, String dgiToken) {
        try {
            LOG.info("=== PHASE 2: Confirmation de la facture UID=" + invoice.uid + " ===");
            
            // Vérification que la facture est en Phase 1
            if (!"PHASE1".equals(invoice.status)) {
                LOG.warning("Facture non prête pour confirmation: " + invoice.rn + " (Statut: " + invoice.status + ")");
                invoice.errorCode = "INVALID_INVOICE_STATUS";
                invoice.errorDesc = "La facture doit être en statut PHASE1 pour être confirmée";
                invoice.persist();
                return invoice;
            }
            
            // Vérification que l'UID existe
            if (invoice.uid == null || invoice.uid.isEmpty()) {
                LOG.warning("UID manquant pour la confirmation");
                invoice.errorCode = "MISSING_UID";
                invoice.errorDesc = "L'UID de soumission est manquant. Veuillez compléter PHASE 1";
                invoice.persist();
                return invoice;
            }
            
            // Étape 2: Confirmation
            JsonNode confirmationResponse = confirmInvoiceWithDgi(invoice, dgiToken);
            
            if (hasError(confirmationResponse)) {
                LOG.warning("Erreur lors de la confirmation Phase 2: " + confirmationResponse.toString());
                invoice.errorCode = extractField(confirmationResponse, "errorCode");
                invoice.errorDesc = extractField(confirmationResponse, "errorDesc");
                // Reste en PHASE1 en cas d'erreur confirmation
                invoice.status = "PHASE1";
                invoice.persist();
                return invoice;
            }
            
            // Extraction et mise à jour des données de confirmation
            invoice.qrCode = extractField(confirmationResponse, "qrCode");
            invoice.dateTime = extractField(confirmationResponse, "dateTime");
            invoice.codeDEFDGI = extractField(confirmationResponse, "codeDEFDGI");
            invoice.counters = extractField(confirmationResponse, "counters");
            invoice.nim = extractField(confirmationResponse, "nim");
            invoice.status = "CONFIRMED";
            invoice.errorCode = null;
            invoice.errorDesc = null;
            
            // Sauvegarde de l'entité
            invoice.persist();
            
            LOG.info("✓ PHASE 2 complétée avec succès - QR Code: " + invoice.qrCode);
            
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Exception PHASE 2: " + e.getMessage(), e);
            invoice.status = "PHASE1";
            invoice.errorCode = "DGI_PHASE2_ERROR";
            invoice.errorDesc = "Erreur lors de la confirmation: " + e.getMessage();
            invoice.persist();
        }
        
        return invoice;
    }

    /**
     * Soumet automatiquement une facture complètement (PHASE 1 + PHASE 2)
     * Retourne l'InvoiceEntity mise à jour
     * 
     * @param invoice La facture à soumettre
     * @param dgiToken Le token d'authentification DGI
     * @return InvoiceEntity mise à jour avec status et erreurs
     */
    @Retry(maxRetries = 2, delay = 2, delayUnit = ChronoUnit.SECONDS)
    public InvoiceEntity submitInvoice(InvoiceEntity invoice, String dgiToken) {
        LOG.info("=== Soumission complète de la facture (PHASE 1 + PHASE 2) ===");
        
        // PHASE 1: Soumission
        invoice = submitInvoicePhase1(invoice, dgiToken);
        
        // Si Phase 1 échoue, retourner l'entité avec l'erreur
        if (!("PHASE1".equals(invoice.status))) {
            return invoice;
        }
        
        // PHASE 2: Confirmation
        return confirmInvoicePhase2(invoice, dgiToken);
    }

    /**
     * Effectue la requête HTTP de soumission à la DGI
     */
    private JsonNode submitInvoiceToDgi(InvoiceEntity invoice, String dgiToken) 
            throws IOException, InterruptedException {
        String jsonPayload = mapper.writeValueAsString(invoice);
        
        LOG.fine("Payload soumission: " + jsonPayload.substring(0, Math.min(100, jsonPayload.length())) + "...");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DGI_API_BASE_URL))
                .timeout(Duration.ofSeconds(DGI_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + dgiToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        LOG.info("[PHASE 1 Response] HTTP " + response.statusCode());
        LOG.fine("Response body: " + response.body());

        return mapper.readTree(response.body());
    }

    /**
     * Effectue la requête HTTP de confirmation à la DGI
     */
    private JsonNode confirmInvoiceWithDgi(InvoiceEntity invoice, String dgiToken) 
            throws IOException, InterruptedException {
        
        Map<String, Object> confirmData = Map.of(
                "total", invoice.total != null ? invoice.total : BigDecimal.ZERO,
                "vtotal", invoice.vtotal != null ? invoice.vtotal : BigDecimal.ZERO
        );

        String jsonPayload = mapper.writeValueAsString(confirmData);
        
        LOG.fine("Payload confirmation: " + jsonPayload);
        
        String confirmUrl = DGI_API_BASE_URL + "/" + invoice.uid + "/confirm";
        LOG.info("URL de confirmation: " + confirmUrl);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(confirmUrl))
                .timeout(Duration.ofSeconds(DGI_TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + dgiToken)
                .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        LOG.info("[PHASE 2 Response] HTTP " + response.statusCode());
        LOG.fine("Response body: " + response.body());

        return mapper.readTree(response.body());
    }

    /**
     * Vérifie si la réponse contient une erreur DGI
     */
    private boolean hasError(JsonNode response) {
        return response.has("errorCode") || response.has("errorDesc") || 
               response.has("error");
    }

    /**
     * Traite les erreurs reçues de la DGI
     */
    private DgiResponse handleDgiError(JsonNode errorResponse) {
        DgiResponse dgiResponse = new DgiResponse();
        dgiResponse.success = false;
        dgiResponse.errorCode = extractField(errorResponse, "errorCode");
        dgiResponse.errorDesc = extractField(errorResponse, "errorDesc");
        
        // Fallback si errorDesc n'existe pas
        if (dgiResponse.errorDesc == null) {
            dgiResponse.errorDesc = extractField(errorResponse, "error");
        }
        
        // Fallback sur le message d'erreur brut si tout est nul
        if (dgiResponse.errorCode == null && dgiResponse.errorDesc == null) {
            dgiResponse.errorCode = "UNKNOWN_ERROR";
            dgiResponse.errorDesc = "Erreur DGI non identifiée: " + errorResponse.asText();
        }
        
        LOG.warning("Erreur DGI reçue - Code: " + dgiResponse.errorCode + 
                    ", Description: " + dgiResponse.errorDesc);

        return dgiResponse;
    }

    /**
     * Extrait un champ texte de la réponse JSON
     */
    private String extractField(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        return field.isNull() ? null : field.asText();
    }

    /**
     * Extrait un BigDecimal de la réponse JSON
     */
    private BigDecimal extractBigDecimal(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return BigDecimal.ZERO;
        }
        
        JsonNode value = node.get(fieldName);
        
        if (value.isNull()) {
            return BigDecimal.ZERO;
        } else if (value.isNumber()) {
            try {
                return value.decimalValue();
            } catch (Exception e) {
                LOG.warning("Erreur conversion BigDecimal pour " + fieldName);
                return BigDecimal.ZERO;
            }
        } else if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException e) {
                LOG.warning("Impossible de convertir '" + value.asText() + "' en BigDecimal");
                return BigDecimal.ZERO;
            }
        }
        
        return BigDecimal.ZERO;
    }
}