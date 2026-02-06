package org.middleware.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DTO simplifié représentant une ligne de facture dans le fichier Excel.
 * L'utilisateur fournit les colonnes de base, le système complète les informations manquantes.
 * 
 * Format Excel attendu (colonnes):
 * A: rn (Numéro de facture - OBLIGATOIRE)
 * B: type (FN=Normal, FA=Avoir, FP=Proforma)
 * C: clientNif (NIF du client)
 * D: clientName (Nom du client)
 * E: itemCode (Code article - OBLIGATOIRE SFE)
 * F: itemName (Nom article - OBLIGATOIRE)
 * G: itemPrice (Prix unitaire - OBLIGATOIRE)
 * H: itemQuantity (Quantité - OBLIGATOIRE)
 * I: itemTaxGroup (Groupe TVA: A-N selon SFE)
 * J: itemArticleType (Type article: BIE, SER, TAX)
 * K: unitPriceMode (Mode prix: HT, TTC)
 * L: currency (Devise: CDF, USD, DH)
 * M: unit (Unité de mesure)
 * N: specificTaxAmount (Montant taxe spécifique)
 * O: mode (Mode: 0=normal, 1=spécial)
 * 
 * Colonnes ajoutées par le système:
 * P: nif (NIF de l'entreprise)
 * Q: isf (ISF de l'entreprise)
 * R: companyName (Nom de l'entreprise)
 * S: taxRate (Taux TVA calculé)
 * T: subtotal (Calculé)
 * U: taxAmount (Montant TVA)
 * V: total (Calculé avec TVA)
 * W: status (Statut de validation)
 * X: errorMessage (Message d'erreur si invalide)
 * 
 * CONTRAINTES SFE (Type Article vs Groupe TVA):
 * - BIE/SER: Valides pour groupes A-K, M (pas L, N)
 * - TAX: Uniquement pour groupes L et N
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
    
    /** Code de l'article (OBLIGATOIRE SFE) */
    public String itemCode;
    
    /** Nom de l'article (OBLIGATOIRE) */
    public String itemName;
    
    /** Prix unitaire de l'article (OBLIGATOIRE) */
    public BigDecimal itemPrice;
    
    /** Quantité (OBLIGATOIRE) */
    public BigDecimal itemQuantity;
    
    /** 
     * Groupe de taxation SFE (A-N):
     * A=Exonéré, B=16%, C=8%, D=Régimes dérogatoires,
     * E=Export, F=Marché public 16%, G=Marché public 8%,
     * H=Consignation, I=Garantie, J=Débours,
     * K=Non assujettis, L=Prélèvements, M=Ventes réglementées, N=TVA spécifique
     */
    public String itemTaxGroup;
    
    /** 
     * Type d'article SFE:
     * BIE=Bien (groupes A-K, M), SER=Service (groupes A-K, M), TAX=Taxes (groupes L, N uniquement)
     */
    public String itemArticleType;
    
    /** Mode de prix: HT (Hors Taxe) ou TTC (Toutes Taxes Comprises) */
    public String unitPriceMode;
    
    /** Devise (CDF, USD, DH) */
    public String currency;
    
    /** Unité de mesure (pièce, kg, heure, etc.) */
    public String unit;
    
    /** Montant de taxe spécifique (optionnel) */
    public BigDecimal specificTaxAmount;
    
    /** Mode de facturation (0=normal, 1=spécial) */
    public String mode;

    // === COLONNES AJOUTÉES PAR LE SYSTÈME ===
    
    /** NIF de l'entreprise (depuis la base de données) */
    public String nif;
    
    /** ISF de l'entreprise (depuis la base de données) */
    public String isf;
    
    /** Nom de l'entreprise */
    public String companyName;
    
    /** Taux de TVA calculé selon le groupe (en %) */
    public BigDecimal taxRate;
    
    /** Sous-total calculé (prix × quantité) */
    public BigDecimal subtotal;
    
    /** Montant TVA calculé */
    public BigDecimal taxAmount;
    
    /** Total avec TVA et taxes spécifiques */
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
            // Calcul du taux TVA selon le groupe
            this.taxRate = getTaxRateValue();
            
            if ("TTC".equalsIgnoreCase(unitPriceMode)) {
                // Prix TTC: on déduit la TVA pour avoir le HT
                BigDecimal divisor = BigDecimal.ONE.add(this.taxRate.divide(new BigDecimal("100")));
                BigDecimal priceHT = itemPrice.divide(divisor, 2, RoundingMode.HALF_UP);
                this.subtotal = priceHT.multiply(itemQuantity).setScale(2, RoundingMode.HALF_UP);
            } else {
                // Prix HT (par défaut)
                this.subtotal = itemPrice.multiply(itemQuantity).setScale(2, RoundingMode.HALF_UP);
            }
            
            // Calcul TVA
            this.taxAmount = this.subtotal.multiply(this.taxRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            
            // Ajout taxe spécifique si présente
            BigDecimal specificTax = BigDecimal.ZERO;
            if (specificTaxAmount != null && specificTaxAmount.compareTo(BigDecimal.ZERO) > 0) {
                specificTax = specificTaxAmount.multiply(itemQuantity).setScale(2, RoundingMode.HALF_UP);
            }
            
            this.total = this.subtotal.add(this.taxAmount).add(specificTax);
        }
    }

    /**
     * Retourne le taux de TVA selon le groupe SFE (en pourcentage)
     */
    private BigDecimal getTaxRateValue() {
        if (itemTaxGroup == null) return BigDecimal.ZERO;
        
        return switch (itemTaxGroup.toUpperCase()) {
            case "B", "F" -> new BigDecimal("16"); // 16% TVA standard / Marché public
            case "C", "G" -> new BigDecimal("8");  // 8% TVA réduit / Marché public
            case "A", "D", "E", "H", "I", "J", "K", "L", "M", "N" -> BigDecimal.ZERO; // Exonéré ou spécifique
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Valide la ligne selon les règles SFE et retourne true si valide
     */
    public boolean validate() {
        StringBuilder errors = new StringBuilder();
        
        // === VALIDATIONS OBLIGATOIRES ===
        if (rn == null || rn.trim().isEmpty()) {
            errors.append("RN obligatoire; ");
        }
        
        if (itemCode == null || itemCode.trim().isEmpty()) {
            errors.append("Code article obligatoire (SFE); ");
        }
        
        if (itemName == null || itemName.trim().isEmpty()) {
            errors.append("Nom article obligatoire; ");
        }
        
        if (itemPrice == null || itemPrice.compareTo(BigDecimal.ZERO) <= 0) {
            errors.append("Prix invalide (doit être > 0); ");
        }
        
        if (itemQuantity == null || itemQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            errors.append("Quantité invalide (doit être > 0); ");
        }
        
        // === VALIDATION CONTRAINTE SFE: Type Article vs Groupe TVA ===
        if (itemArticleType != null && itemTaxGroup != null) {
            String artType = itemArticleType.toUpperCase();
            String taxGrp = itemTaxGroup.toUpperCase();
            
            // Contrainte: TAX uniquement pour groupes L et N
            if ("TAX".equals(artType)) {
                if (!"L".equals(taxGrp) && !"N".equals(taxGrp)) {
                    errors.append("Type TAX uniquement autorisé pour groupes L et N (groupe actuel: ").append(taxGrp).append("); ");
                }
            }
            
            // Contrainte: Groupes L et N exigent type TAX
            if (("L".equals(taxGrp) || "N".equals(taxGrp)) && !"TAX".equals(artType)) {
                errors.append("Groupes L et N exigent type article TAX (type actuel: ").append(artType).append("); ");
            }
        }
        
        // === VALIDATION GROUPE TVA ===
        if (itemTaxGroup != null && !itemTaxGroup.trim().isEmpty()) {
            String validGroups = "ABCDEFGHIJKLMN";
            if (!validGroups.contains(itemTaxGroup.toUpperCase())) {
                errors.append("Groupe TVA invalide (doit être A-N); ");
            }
        }
        
        // === VALIDATION TYPE ARTICLE ===
        if (itemArticleType != null && !itemArticleType.trim().isEmpty()) {
            String artType = itemArticleType.toUpperCase();
            if (!"BIE".equals(artType) && !"SER".equals(artType) && !"TAX".equals(artType)) {
                errors.append("Type article invalide (BIE, SER ou TAX); ");
            }
        }
        
        // === VALIDATION MODE PRIX ===
        if (unitPriceMode != null && !unitPriceMode.trim().isEmpty()) {
            String priceMode = unitPriceMode.toUpperCase();
            if (!"HT".equals(priceMode) && !"TTC".equals(priceMode)) {
                errors.append("Mode prix invalide (HT ou TTC); ");
            }
        }
        
        // === VALIDATION DEVISE ===
        if (currency != null && !currency.trim().isEmpty()) {
            String curr = currency.toUpperCase();
            if (!"CDF".equals(curr) && !"USD".equals(curr) && !"DH".equals(curr)) {
                errors.append("Devise invalide (CDF, USD ou DH); ");
            }
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
     * Normalise les valeurs par défaut selon les spécifications SFE
     */
    public void normalize() {
        // Type de facture par défaut: Facture Normale
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
        
        // Mode de facturation par défaut: normal
        if (mode == null || mode.trim().isEmpty()) {
            mode = "0";
        }
        
        // Groupe TVA par défaut: B (16% - Taxable standard)
        if (itemTaxGroup == null || itemTaxGroup.trim().isEmpty()) {
            itemTaxGroup = "B";
        } else {
            itemTaxGroup = itemTaxGroup.toUpperCase().trim();
        }
        
        // Type d'article par défaut selon le groupe TVA
        if (itemArticleType == null || itemArticleType.trim().isEmpty()) {
            // Si groupe L ou N, type TAX obligatoire
            if ("L".equals(itemTaxGroup) || "N".equals(itemTaxGroup)) {
                itemArticleType = "TAX";
            } else {
                itemArticleType = "BIE"; // Bien par défaut
            }
        } else {
            itemArticleType = itemArticleType.toUpperCase().trim();
        }
        
        // Mode de prix par défaut: HT
        if (unitPriceMode == null || unitPriceMode.trim().isEmpty()) {
            unitPriceMode = "HT";
        } else {
            unitPriceMode = unitPriceMode.toUpperCase().trim();
        }
        
        // Unité par défaut
        if (unit == null || unit.trim().isEmpty()) {
            unit = "pcs";
        }
        
        // Code article: générer si absent mais nom présent
        if ((itemCode == null || itemCode.trim().isEmpty()) && itemName != null && !itemName.trim().isEmpty()) {
            itemCode = "ART-" + Math.abs(itemName.hashCode());
        }
        
        // Taxe spécifique: null si zéro
        if (specificTaxAmount != null && specificTaxAmount.compareTo(BigDecimal.ZERO) == 0) {
            specificTaxAmount = null;
        }
    }
    
    /**
     * Retourne une description lisible du groupe de taxation
     */
    public String getTaxGroupDescription() {
        if (itemTaxGroup == null) return "";
        
        return switch (itemTaxGroup.toUpperCase()) {
            case "A" -> "Exonéré";
            case "B" -> "Taxable 16%";
            case "C" -> "Taxable 8%";
            case "D" -> "Régimes dérogatoires TVA";
            case "E" -> "Exportation";
            case "F" -> "Marché public 16%";
            case "G" -> "Marché public 8%";
            case "H" -> "Consignation/déconsignation";
            case "I" -> "Garantie et caution";
            case "J" -> "Débours";
            case "K" -> "Non assujettis";
            case "L" -> "Prélèvements sur ventes";
            case "M" -> "Ventes réglementées TVA spécifique";
            case "N" -> "TVA spécifique";
            default -> "Inconnu";
        };
    }
    
    /**
     * Retourne une description lisible du type d'article
     */
    public String getArticleTypeDescription() {
        if (itemArticleType == null) return "";
        
        return switch (itemArticleType.toUpperCase()) {
            case "BIE" -> "Bien";
            case "SER" -> "Service";
            case "TAX" -> "Taxes et redevances";
            default -> "Inconnu";
        };
    }
}
