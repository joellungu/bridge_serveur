package org.middleware.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;

/**
 * DTO simplifié représentant une ligne de facture dans le fichier Excel.
 * L'utilisateur fournit les colonnes de base, le système complète les informations manquantes.
 * 
 * Format Excel attendu (colonnes):
 * A: rn (Numéro de facture - OBLIGATOIRE)
 * B: type (FN=Normal, FA=Avoir, FP=Proforma)
 * C: clientNif (NIF du client)
 * D: clientName (Nom du client)
 * E: itemCode (Code article)
 * F: itemName (Nom article)
 * G: itemPrice (Prix unitaire)
 * H: itemQuantity (Quantité)
 * I: itemTaxGroup (Groupe TVA: A, B, C, D)
 * J: currency (Devise: CDF, USD)
 * K: mode (Mode: 0=normal, 1=spécial)
 * 
 * Colonnes ajoutées par le système:
 * L: nif (NIF de l'entreprise)
 * M: isf (ISF de l'entreprise)
 * N: companyName (Nom de l'entreprise)
 * O: subtotal (Calculé)
 * P: total (Calculé avec TVA)
 * Q: status (Statut de validation)
 * R: errorMessage (Message d'erreur si invalide)
 */
@RegisterForReflection
public class ExcelInvoiceRow {

    // === COLONNES FOURNIES PAR L'UTILISATEUR ===
    
    /** Numéro de référence de la facture (OBLIGATOIRE) */
    public String rn;
    
    /** Type de facture: FN (Normal), FA (Avoir), FP (Proforma) */
    public String type;
    
    /** NIF du client */
    public String clientNif;
    
    /** Nom du client */
    public String clientName;
    
    /** Code de l'article */
    public String itemCode;
    
    /** Nom de l'article */
    public String itemName;
    
    /** Prix unitaire de l'article */
    public BigDecimal itemPrice;
    
    /** Quantité */
    public BigDecimal itemQuantity;
    
    /** Groupe de taxe (A=16%, B=8%, C=0%, D=Exonéré) */
    public String itemTaxGroup;
    
    /** Devise (CDF ou USD) */
    public String currency;
    
    /** Mode de facturation (0=normal, 1=spécial) */
    public String mode;

    // === COLONNES AJOUTÉES PAR LE SYSTÈME ===
    
    /** NIF de l'entreprise (depuis la base de données) */
    public String nif;
    
    /** ISF de l'entreprise (depuis la base de données) */
    public String isf;
    
    /** Nom de l'entreprise */
    public String companyName;
    
    /** Sous-total calculé (prix × quantité) */
    public BigDecimal subtotal;
    
    /** Total avec TVA */
    public BigDecimal total;
    
    /** Statut de validation: VALID, INVALID, DUPLICATE */
    public String status;
    
    /** Message d'erreur si la ligne est invalide */
    public String errorMessage;

    // === CONSTRUCTEURS ===
    
    public ExcelInvoiceRow() {
    }

    // === MÉTHODES UTILITAIRES ===
    
    /**
     * Calcule les montants (subtotal et total) basés sur les données de l'article
     */
    public void calculateTotals() {
        if (itemPrice != null && itemQuantity != null) {
            this.subtotal = itemPrice.multiply(itemQuantity);
            
            // Calcul TVA selon le groupe
            BigDecimal taxRate = getTaxRate();
            BigDecimal taxAmount = this.subtotal.multiply(taxRate);
            this.total = this.subtotal.add(taxAmount);
        }
    }

    /**
     * Retourne le taux de TVA selon le groupe
     */
    private BigDecimal getTaxRate() {
        if (itemTaxGroup == null) return BigDecimal.ZERO;
        
        return switch (itemTaxGroup.toUpperCase()) {
            case "A" -> new BigDecimal("0.16"); // 16% TVA standard
            case "B" -> new BigDecimal("0.08"); // 8% TVA réduit
            case "C", "D" -> BigDecimal.ZERO;   // 0% ou exonéré
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Valide la ligne et retourne true si valide
     */
    public boolean validate() {
        StringBuilder errors = new StringBuilder();
        
        if (rn == null || rn.trim().isEmpty()) {
            errors.append("RN obligatoire; ");
        }
        
        if (itemPrice == null || itemPrice.compareTo(BigDecimal.ZERO) <= 0) {
            errors.append("Prix invalide; ");
        }
        
        if (itemQuantity == null || itemQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            errors.append("Quantité invalide; ");
        }
        
        if (errors.length() > 0) {
            this.status = "INVALID";
            this.errorMessage = errors.toString();
            return false;
        }
        
        this.status = "VALID";
        return true;
    }

    /**
     * Normalise les valeurs par défaut
     */
    public void normalize() {
        // Type par défaut: Facture Normale
        if (type == null || type.trim().isEmpty()) {
            type = "FN";
        } else {
            type = type.toUpperCase().trim();
        }
        
        // Devise par défaut: CDF
        if (currency == null || currency.trim().isEmpty()) {
            currency = "CDF";
        } else {
            currency = currency.toUpperCase().trim();
        }
        
        // Mode par défaut: normal
        if (mode == null || mode.trim().isEmpty()) {
            mode = "0";
        }
        
        // Groupe TVA par défaut: A (16%)
        if (itemTaxGroup == null || itemTaxGroup.trim().isEmpty()) {
            itemTaxGroup = "A";
        } else {
            itemTaxGroup = itemTaxGroup.toUpperCase().trim();
        }
        
        // Code article par défaut si nom fourni
        if ((itemCode == null || itemCode.trim().isEmpty()) && itemName != null) {
            itemCode = "ART-" + itemName.hashCode();
        }
    }
}
