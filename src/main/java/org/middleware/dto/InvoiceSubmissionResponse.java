package org.middleware.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Réponse structurée pour la soumission de facture en deux étapes
 * 
 * Cette classe encapsule:
 * - Les données de PHASE 1 (Soumission initial)
 * - Les données de PHASE 2 (Confirmation)
 * - Les informations d'erreur
 */
@RegisterForReflection
public class InvoiceSubmissionResponse {
    
    // ===== INFORMATIONS GÉNÉRALES =====
    public String invoiceNumber;    // RN - Numéro de facture
    public String status;           // Statut global (PHASE1, CONFIRMED, FAILED)
    public boolean isComplete;      // true si les deux phases sont complétées
    
    // ===== PHASE 1: SOUMISSION =====
    public Phase1Data phase1;
    
    // ===== PHASE 2: CONFIRMATION =====
    public Phase2Data phase2;
    
    // ===== GESTION DES ERREURS =====
    public ErrorInfo error;
    
    // ===== MÉTADONNÉES =====
    public LocalDateTime createdAt;
    public String serverTimestamp;
    
    public InvoiceSubmissionResponse() {
        this.createdAt = LocalDateTime.now();
        this.serverTimestamp = LocalDateTime.now().toString();
    }
    
    /**
     * Classe imbriquée pour les données PHASE 1
     */
    @RegisterForReflection
    public static class Phase1Data {
        public String uid;                  // Identifiant unique DGI
        public BigDecimal total;            // Total facture
        public BigDecimal curTotal;         // Total en devise
        public BigDecimal vtotal;           // Total TVA
        public boolean success;
        public String message;              // Message utilisateur
        
        public Phase1Data() {}
        
        public Phase1Data(String uid, BigDecimal total, BigDecimal curTotal, 
                         BigDecimal vtotal, boolean success, String message) {
            this.uid = uid;
            this.total = total;
            this.curTotal = curTotal;
            this.vtotal = vtotal;
            this.success = success;
            this.message = message;
        }
    }
    
    /**
     * Classe imbriquée pour les données PHASE 2
     */
    @RegisterForReflection
    public static class Phase2Data {
        public String qrCode;               // Code QR pour lecture
        public String dateTime;             // Date/heure validation
        public String codeDEFDGI;           // Code official DGI
        public String counters;             // Compteurs
        public String nim;                  // Numéro identification
        public boolean success;
        public String message;              // Message utilisateur
        
        public Phase2Data() {}
        
        public Phase2Data(String qrCode, String dateTime, String codeDEFDGI,
                         String counters, String nim, boolean success, String message) {
            this.qrCode = qrCode;
            this.dateTime = dateTime;
            this.codeDEFDGI = codeDEFDGI;
            this.counters = counters;
            this.nim = nim;
            this.success = success;
            this.message = message;
        }
    }
    
    /**
     * Classe pour la gestion des erreurs
     */
    @RegisterForReflection
    public static class ErrorInfo {
        public String code;                 // Code d'erreur
        public String description;          // Description détaillée
        public String userMessage;          // Message pour l'utilisateur final
        public String failedPhase;          // "PHASE1" ou "PHASE2"
        public Map<String, Object> details; // Détails additionnels
        
        public ErrorInfo() {
            this.details = new HashMap<>();
        }
        
        public ErrorInfo(String code, String description, String userMessage, String failedPhase) {
            this();
            this.code = code;
            this.description = description;
            this.userMessage = userMessage;
            this.failedPhase = failedPhase;
        }
    }
    
    // ===== MÉTHODES UTILITAIRES =====
    
    /**
     * Indique si la soumission a complètement réussi
     */
    public boolean isSuccessful() {
        return isComplete && (phase2 != null && phase2.success);
    }
    
    /**
     * Indique si on est bloqué à la phase 1
     */
    public boolean isPhase1Only() {
        return "PHASE1".equals(status) && (phase2 == null || !phase2.success);
    }
    
    /**
     * Message structuré pour l'utilisateur
     */
    public Map<String, Object> toUserFriendlyResponse() {
        Map<String, Object> response = new HashMap<>();
        
        if (isSuccessful()) {
            response.put("success", true);
            response.put("message", "✓ Facture validée et confirmée par la DGI");
            response.put("invoice", Map.of(
                "number", invoiceNumber,
                "status", "CONFIRMED"
            ));
            response.put("phase1", Map.of(
                "uid", phase1.uid,
                "total", phase1.total
            ));
            response.put("phase2", Map.of(
                "qrCode", phase2.qrCode,
                "dateTime", phase2.dateTime,
                "codeDEFDGI", phase2.codeDEFDGI
            ));
        } else if (isPhase1Only()) {
            response.put("success", true);
            response.put("message", "⏳ Facture soumise. Awaiting confirmation...");
            response.put("invoice", Map.of(
                "number", invoiceNumber,
                "status", "PHASE1",
                "uid", phase1.uid
            ));
            response.put("nextStep", "Veuillez confirmer la soumission avec le UID reçu");
        } else if (error != null) {
            response.put("success", false);
            response.put("message", "✗ Erreur lors du traitement");
            response.put("error", Map.of(
                "code", error.code,
                "message", error.userMessage,
                "failedPhase", error.failedPhase
            ));
        }
        
        return response;
    }

    @Override
    public String toString() {
        return "InvoiceSubmissionResponse{" +
                "invoiceNumber='" + invoiceNumber + '\'' +
                ", status='" + status + '\'' +
                ", isComplete=" + isComplete +
                ", phase1=" + (phase1 != null ? phase1.uid : "null") +
                ", phase2=" + (phase2 != null ? phase2.qrCode : "null") +
                '}';
    }
}
