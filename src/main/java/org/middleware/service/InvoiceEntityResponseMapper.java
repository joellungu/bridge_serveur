package org.middleware.service;

import java.util.HashMap;
import java.util.Map;

import org.middleware.models.InvoiceEntity;

/**
 * Transforme une InvoiceEntity en réponse utilisateur claire
 * Gère les messages et statuts pour chaque phase
 */
public class InvoiceEntityResponseMapper {
    
    /**
     * Mappe l'InvoiceEntity en réponse utilisateur lisible
     */
    public static Map<String, Object> toUserResponse(InvoiceEntity invoice) {
        Map<String, Object> response = new HashMap<>();
        
        // Informations de base
        response.put("invoiceNumber", invoice.rn);
        response.put("status", invoice.status);
        response.put("uid", invoice.uid);
        
        if ("CONFIRMED".equals(invoice.status)) {
            // ✅ Succès complet (Phase 1 + Phase 2)
            response.put("success", true);
            response.put("message", "✓ Facture validée et confirmée par la DGI");
            
            Map<String, Object> submissionData = new HashMap<>();
            submissionData.put("uid", invoice.uid);
            submissionData.put("total", invoice.total);
            submissionData.put("curTotal", invoice.curTotal);
            submissionData.put("vtotal", invoice.vtotal);
            submissionData.put("status", "PHASE1");
            response.put("submission", submissionData);
            
            Map<String, Object> confirmationData = new HashMap<>();
            confirmationData.put("qrCode", invoice.qrCode);
            confirmationData.put("dateTime", invoice.dateTime);
            confirmationData.put("codeDEFDGI", invoice.codeDEFDGI);
            confirmationData.put("counters", invoice.counters);
            confirmationData.put("nim", invoice.nim);
            confirmationData.put("status", "CONFIRMED");
            response.put("confirmation", confirmationData);
            
        } else if ("PHASE1".equals(invoice.status) && invoice.uid != null && !invoice.uid.trim().isEmpty()) {
            // ⏳ Phase 1 complétée AVEC UID valide, en attente de Phase 2
            response.put("success", true);
            response.put("message", "⏳ Normalisation PHASE 1 effectuée avec succès. En attente de confirmation PHASE 2.");
            
            Map<String, Object> submissionData = new HashMap<>();
            submissionData.put("uid", invoice.uid);
            submissionData.put("total", invoice.total);
            submissionData.put("curTotal", invoice.curTotal);
            submissionData.put("vtotal", invoice.vtotal);
            submissionData.put("status", "PHASE1");
            response.put("submission", submissionData);
            
            Map<String, Object> nextStep = new HashMap<>();
            nextStep.put("phase", "2");
            nextStep.put("action", "Confirmation de la facture");
            nextStep.put("uid", invoice.uid);
            response.put("nextStep", nextStep);
            
        } else if ("PHASE1".equals(invoice.status) && (invoice.uid == null || invoice.uid.trim().isEmpty())) {
            // ❌ Phase 1 avec statut mais SANS UID = échec
            response.put("success", false);
            response.put("message", "✗ PHASE 1 incomplète: UID manquant. La normalisation a échoué.");
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("code", "MISSING_UID");
            errorData.put("description", "L'UID n'a pas été reçu de la DGI. La PHASE 1 n'est pas terminée.");
            errorData.put("status", invoice.status);
            response.put("error", errorData);
            
        } else if (invoice.errorCode != null || invoice.errorDesc != null) {
            // ❌ Erreur détectée
            response.put("success", false);
            response.put("message", "✗ Erreur lors du traitement");
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("code", invoice.errorCode);
            errorData.put("description", invoice.errorDesc);
            errorData.put("status", invoice.status);
            response.put("error", errorData);
            
            // Si la facture a une Phase 1 valide, l'indiquer
            if (invoice.uid != null && !invoice.uid.trim().isEmpty()) {
                Map<String, Object> submissionData = new HashMap<>();
                submissionData.put("uid", invoice.uid);
                submissionData.put("total", invoice.total);
                response.put("submission", submissionData);
            }
        } else {
            // 📋 Statut indéterminé ou PENDING
            response.put("success", false);
            response.put("message", "Statut: " + (invoice.status != null ? invoice.status : "PENDING"));
            response.put("status", invoice.status);
        }
        
        return response;
    }
    
    /**
     * Message formaté pour chaque statut
     */
    public static String getStatusMessage(String status, String errorCode) {
        if ("CONFIRMED".equals(status)) {
            return "✓ Facture validée et confirmée par la DGI";
        } else if ("PHASE1".equals(status)) {
            return "⏳ Facture soumise. En attente de confirmation.";
        } else if ("PENDING".equals(status) && errorCode != null) {
            return "✗ Erreur: " + errorCode;
        } else {
            return "Statut inconnu";
        }
    }
    
    /**
     * Détermine si la réponse est un succès (statut valide ET UID présent pour PHASE1)
     */
    public static boolean isSuccess(InvoiceEntity invoice) {
        if ("CONFIRMED".equals(invoice.status)) {
            return invoice.errorCode == null && invoice.errorDesc == null;
        } else if ("PHASE1".equals(invoice.status)) {
            // PHASE1 n'est un succès que si l'UID est présent
            return invoice.uid != null && !invoice.uid.trim().isEmpty() &&
                   invoice.errorCode == null && invoice.errorDesc == null;
        }
        return false;
    }
    
    /**
     * Vérifie si une erreur est présente
     */
    public static boolean hasError(InvoiceEntity invoice) {
        return invoice.errorCode != null || invoice.errorDesc != null;
    }
}
