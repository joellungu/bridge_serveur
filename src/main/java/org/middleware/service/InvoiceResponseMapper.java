package org.middleware.service;

import org.middleware.dto.DgiResponse;
import org.middleware.dto.InvoiceSubmissionResponse;
import org.middleware.models.InvoiceEntity;

import java.util.logging.Logger;

/**
 * Utilitaire pour transformer les réponses DGI en réponses utilisateur
 * Gère la conversion des données techniques en messages clairs
 */
public class InvoiceResponseMapper {
    
    private static final Logger LOG = Logger.getLogger(InvoiceResponseMapper.class.getName());
    
    /**
     * Mappe une réponse PHASE 1 en réponse utilisateur
     */
    public static InvoiceSubmissionResponse mapPhase1Response(InvoiceEntity invoice, DgiResponse dgiResponse) {
        InvoiceSubmissionResponse response = new InvoiceSubmissionResponse();
        response.invoiceNumber = invoice.rn;
        
        if (dgiResponse.success) {
            response.phase1 = new InvoiceSubmissionResponse.Phase1Data(
                dgiResponse.uid,
                dgiResponse.total,
                dgiResponse.curTotal,
                dgiResponse.vtotal,
                true,
                "Facture soumise avec succès à la DGI"
            );
            response.status = "PHASE1";
            response.isComplete = false;
        } else {
            response.error = new InvoiceSubmissionResponse.ErrorInfo(
                dgiResponse.errorCode,
                dgiResponse.errorDesc,
                "Erreur lors de la soumission: " + dgiResponse.errorDesc,
                "PHASE1"
            );
            response.status = "FAILED";
            response.isComplete = false;
        }
        
        return response;
    }
    
    /**
     * Mappe une réponse PHASE 2 en réponse utilisateur
     */
    public static InvoiceSubmissionResponse mapPhase2Response(InvoiceEntity invoice, DgiResponse dgiResponse) {
        InvoiceSubmissionResponse response = new InvoiceSubmissionResponse();
        response.invoiceNumber = invoice.rn;
        
        // Ajouter la phase 1 si disponible
        if (invoice.uid != null) {
            response.phase1 = new InvoiceSubmissionResponse.Phase1Data(
                invoice.uid,
                invoice.total,
                invoice.curTotal,
                invoice.vtotal,
                true,
                "Facture soumise"
            );
        }
        
        if (dgiResponse.success) {
            response.phase2 = new InvoiceSubmissionResponse.Phase2Data(
                dgiResponse.qrCode,
                dgiResponse.dateTime,
                dgiResponse.codeDEFDGI,
                dgiResponse.counters,
                dgiResponse.nim,
                true,
                "Facture validée et confirmée par la DGI"
            );
            response.status = "CONFIRMED";
            response.isComplete = true;
        } else {
            response.error = new InvoiceSubmissionResponse.ErrorInfo(
                dgiResponse.errorCode,
                dgiResponse.errorDesc,
                "Erreur lors de la confirmation: " + dgiResponse.errorDesc,
                "PHASE2"
            );
            response.status = "FAILED";
            response.isComplete = false;
        }
        
        return response;
    }
    
    /**
     * Mappe une réponse complète (PHASE 1 + PHASE 2)
     */
    public static InvoiceSubmissionResponse mapCompleteResponse(InvoiceEntity invoice, 
                                                                  DgiResponse phase1Response,
                                                                  DgiResponse phase2Response) {
        InvoiceSubmissionResponse response = new InvoiceSubmissionResponse();
        response.invoiceNumber = invoice.rn;
        
        // Phase 1
        if (phase1Response.success) {
            response.phase1 = new InvoiceSubmissionResponse.Phase1Data(
                phase1Response.uid,
                phase1Response.total,
                phase1Response.curTotal,
                phase1Response.vtotal,
                true,
                "Soumission réussie"
            );
        } else {
            response.error = new InvoiceSubmissionResponse.ErrorInfo(
                phase1Response.errorCode,
                phase1Response.errorDesc,
                "Erreur Phase 1: " + phase1Response.errorDesc,
                "PHASE1"
            );
            response.status = "FAILED";
            response.isComplete = false;
            return response;
        }
        
        // Phase 2
        if (phase2Response.success) {
            response.phase2 = new InvoiceSubmissionResponse.Phase2Data(
                phase2Response.qrCode,
                phase2Response.dateTime,
                phase2Response.codeDEFDGI,
                phase2Response.counters,
                phase2Response.nim,
                true,
                "Confirmation réussie"
            );
            response.status = "CONFIRMED";
            response.isComplete = true;
        } else {
            response.error = new InvoiceSubmissionResponse.ErrorInfo(
                phase2Response.errorCode,
                phase2Response.errorDesc,
                "Erreur Phase 2: " + phase2Response.errorDesc,
                "PHASE2"
            );
            response.status = "PHASE1"; // Reste en Phase 1 même avec erreur en Phase 2
            response.isComplete = false;
        }
        
        return response;
    }
    
    /**
     * Messages informatifs pour chaque statut
     */
    public static String getStatusMessage(String status) {
        return switch (status) {
            case "PHASE1" -> "✓ Facture soumise avec succès. En attente de confirmation.";
            case "CONFIRMED" -> "✓ Facture validée et confirmée par la DGI.";
            case "FAILED" -> "✗ Erreur lors du traitement de la facture.";
            default -> "Statut inconnu";
        };
    }
}
