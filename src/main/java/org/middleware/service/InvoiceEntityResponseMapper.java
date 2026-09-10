package org.middleware.service;

import org.middleware.models.InvoiceEntity;
import java.util.HashMap;
import java.util.Map;

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
            
        } else if ("PHASE1".equals(invoice.status)) {
            // ⏳ Phase 1 complétée, en attente de Phase 2
            response.put("success", true);
            response.put("message", "⏳ Facture soumise avec succès. En attente de confirmation.");

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

            if (invoice.errorCode != null || invoice.errorDesc != null) {
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("code", invoice.errorCode);
                errorData.put("description", invoice.errorDesc);
                errorData.put("status", "PHASE1");
                response.put("error", errorData);
            }
            
        } else if (invoice.errorCode != null || invoice.errorDesc != null) {
            // ❌ Erreur détectée
            response.put("success", false);
            response.put("message", "✗ Erreur lors du traitement");
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("code", invoice.errorCode);
            errorData.put("description", invoice.errorDesc);
            errorData.put("status", invoice.status);
            response.put("error", errorData);
            
            // Si la facture a une Phase 1, l'indiquer
            if (invoice.uid != null && !invoice.uid.isEmpty()) {
                Map<String, Object> submissionData = new HashMap<>();
                submissionData.put("uid", invoice.uid);
                submissionData.put("total", invoice.total);
                response.put("submission", submissionData);
            }
        } else {
            // 📋 Statut indéterminé
            response.put("success", false);
            response.put("message", "Statut indéterminé");
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
     * Détermine si la réponse est un succès
     */
    public static boolean isSuccess(InvoiceEntity invoice) {
        return ("CONFIRMED".equals(invoice.status) || "PHASE1".equals(invoice.status)) && 
               (invoice.errorCode == null && invoice.errorDesc == null);
    }
    
    /**
     * Vérifie si une erreur est présente
     */
    public static boolean hasError(InvoiceEntity invoice) {
        return invoice.errorCode != null || invoice.errorDesc != null;
    }
}
