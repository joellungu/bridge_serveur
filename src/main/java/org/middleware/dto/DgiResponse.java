package org.middleware.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Réponse standardisée du service DGI
 * 
 * Contient les données de soumission et/ou confirmation d'une facture
 */
@RegisterForReflection
public class DgiResponse {
    // ===== STATUS ET RÉSULTAT =====
    public boolean success;
    public String status;           // PHASE1, CONFIRMED, PHASE1_FAILED, PHASE2_FAILED, etc.
    
    // ===== DONNÉES DE SOUMISSION (PHASE 1) =====
    public String uid;              // Identifiant unique attribué par la DGI
    public BigDecimal total;        // Total de la facture
    public BigDecimal curTotal;     // Total en devise locale
    public BigDecimal vtotal;       // Total TVA
    
    // ===== DONNÉES DE CONFIRMATION (PHASE 2) =====
    public String qrCode;           // Code QR de la facture
    public String dateTime;         // Date/heure de confirmation
    public String codeDEFDGI;       // Code DEF attribué par la DGI
    public String counters;         // Compteurs
    public String nim;              // Numéro d'identification
    
    // ===== GESTION DES ERREURS =====
    public String errorCode;        // Code d'erreur DGI
    public String errorDesc;        // Description de l'erreur
    
    // ===== MÉTADONNÉES =====
    public Map<String, Object> metadata;
    public LocalDateTime responseTimestamp;

    public DgiResponse() {
        this.responseTimestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
        this.success = false;
    }

    /**
     * Indique si la réponse est une phase 1 (soumission)
     */
    public boolean isPhase1() {
        return "PHASE1".equals(status);
    }

    /**
     * Indique si la réponse est confirmée (phase 2 complète)
     */
    public boolean isConfirmed() {
        return "CONFIRMED".equals(status);
    }

    /**
     * Indique s'il y a une erreur
     */
    public boolean hasError() {
        return errorCode != null || errorDesc != null;
    }

    /**
     * Message d'erreur formaté pour l'utilisateur
     */
    public String getErrorMessage() {
        if (errorCode == null && errorDesc == null) {
            return "Erreur inconnue";
        }
        return (errorCode != null ? "[" + errorCode + "] " : "") + 
               (errorDesc != null ? errorDesc : "");
    }

    /**
     * Résumé du statut pour l'utilisateur final
     */
    public String getUserFriendlyStatus() {
        if (success && isConfirmed()) {
            return "✓ Facture confirmée et validée par la DGI";
        } else if (success && isPhase1()) {
            return "⏳ Facture soumise - En attente de confirmation";
        } else if (!success && hasError()) {
            return "✗ Erreur: " + getErrorMessage();
        } else {
            return "Statut inconnu";
        }
    }

    @Override
    public String toString() {
        return "DgiResponse{" +
                "success=" + success +
                ", status='" + status + '\'' +
                ", uid='" + uid + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", errorDesc='" + errorDesc + '\'' +
                '}';
    }
}
