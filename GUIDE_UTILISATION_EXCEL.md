# 📊 Guide Pratique: Utiliser le Fichier Excel Normalisé

## 🎯 Objectif

Convertir des **lignes Excel** en **factures structurées** normalisées par le système DGI avec:
- ✅ Calcul automatique des taxes
- ✅ Groupement d'articles par facture
- ✅ Validation des données
- ✅ Soumission à la DGI (Phase 1 + Phase 2)

---

## 📥 Étape 1: Obtenir le Fichier

### Option A: Utiliser le CSV Fourni
```
📄 exemple_factures_normalisees.csv
```
Ouvrez-le avec **Excel** ou **LibreOffice Calc** et modifiez les données

### Option B: Créer Votre Propre Fichier
Créez une nouvelle feuille Excel avec les colonnes exactes (voir colonne `NIF_ENT` à `DEVISE_PAYE`)

---

## 🔧 Préparation des Données

### 1️⃣ Définir les En-têtes de Colonnes

| Colonne | Contenu | Exemple |
|---------|---------|---------|
| A | NIF_ENT | 12345678 |
| B | RN_ENT | 87654321 |
| C | NUM_FAC | FAC-2025-001 |
| D | TYPE | FA / FC / FE |
| E | MODE | M (Manuel) |
| F | ISF | (Laisser vide si N/A) |
| G | DATE_EMIS | 15/01/2025 |
| H | DATE_PAIE | 20/01/2025 |
| I | DATE_VALID | 15/02/2025 |
| J | DEVISE | TND / EUR / USD |
| K | NIF_CLI | 11223344 |
| L | NOM_CLI | Nom Entreprise Client |
| M | CONTACT_CLI | +216 20 123456 |
| N | ADRESSE_CLI | Rue XXX Tunis |
| O | TYPE_CLI | PE (Personne Exploitant) |
| P | CODE_ART | PROD-001 |
| Q | NOM_ART | Nom du Produit/Service |
| R | TYPE_ART | S (Service) ou P (Produit) |
| S | QTY | 2.5 |
| T | UNIT | h (heures) / u (unité) / kg / l |
| U | PRIX_UNIT | 1000.00 |
| V | GRP_FISCAL | A (19%) / B (13%) / E (7%) / X (0%) |
| W | MONTANT_TAX_SPEC | 0.00 (taxe spécifique si applicable) |
| X | MODE_PAYE | VIR / CHQ / ESP / CB |
| Y | MONTANT_PAYE | 2500.00 |
| Z | DEVISE_PAYE | TND / EUR / USD |

---

### 2️⃣ Remplir les Données

**Important**: 
- ✅ Chaque **ligne** = un **article** d'une facture
- ✅ Même `NUM_FAC` = articles du même document
- ✅ Répéter les données d'en-tête (NIF, RN, Dates) pour chaque article

**Exemple**:
```
FAC-2025-001 contient 2 articles → 2 lignes
FAC-2025-002 contient 1 article → 1 ligne
FAC-2025-003 contient 1 article → 1 ligne
```

---

### 3️⃣ Respecter les Formats

| Champ | Format | ✅ Correct | ❌ Incorrect |
|-------|--------|-----------|------------|
| **Date** | DD/MM/YYYY | 15/01/2025 | 01/15/2025 |
| **NIF/RN** | 8 chiffres | 12345678 | 1234-5678 |
| **Montant** | Décimal | 1000.00 | 1000€ |
| **Taux Taxe** | Groupe (A/B/E/X) | A | 19% |
| **Type Facture** | Code court | FA | Facture d'Achat |
| **Contact** | Téléphone | +216 20 123456 | 0020 123456 |

---

## 📤 Étape 2: Importer le Fichier

### Méthode 1: Via l'API JSON (Recommandé)

1. **Convertir CSV → JSON**:
```json
[
  {
    "nif": "12345678",
    "rn": "87654321",
    "type": "FA",
    "mode": "M",
    "isf": null,
    "currency": "TND",
    "issueDate": "2025-01-15T00:00:00",
    "paymentDate": "2025-01-20T00:00:00",
    "validityDate": "2025-02-15T00:00:00",
    "client": {
      "nif": "11223344",
      "name": "Entreprise ABC SARL",
      "contact": "+216 20 123456",
      "address": "Rue de la Paix Tunis",
      "type": "PE"
    },
    "items": [
      {
        "code": "PROD-001",
        "name": "Consultation Informatique",
        "type": "S",
        "quantity": 2.5,
        "unit": "h",
        "price": 1000.00,
        "taxGroup": "A",
        "taxSpecificAmount": 0.00
      },
      {
        "code": "PROD-002",
        "name": "Fournitures Bureau",
        "type": "P",
        "quantity": 5,
        "unit": "u",
        "price": 50.00,
        "taxGroup": "B",
        "taxSpecificAmount": 0.00
      }
    ],
    "payments": [
      {
        "name": "VIR",
        "amount": 2750.00,
        "currencyCode": "TND"
      }
    ]
  }
]
```

2. **Envoyer POST** à:
```
POST /api/invoices/batch
Content-Type: application/json
Authorization: Bearer <DGI_TOKEN>
```

### Méthode 2: Via Import Excel (Si disponible)

```bash
POST /api/invoices/import
Content-Type: multipart/form-data
File: exemple_factures_normalisees.csv
```

---

## ✅ Validation des Données

### Vérifier Avant d'Importer

| Vérification | Exemple |
|-------------|---------|
| NIF/RN uniques? | Tous les articles ont même NIF/RN pour l'entreprise |
| Dates cohérentes? | dateEmis ≤ datePaie ≤ dateValidite |
| Quantités positives? | QTY > 0 (sauf facture d'avoir où QTY peut être négatif) |
| Montants décimaux? | 1000.00 (pas 1.000,00) |
| Type facture valide? | FA / FC / FE seulement |
| Devise ISO? | TND / EUR / USD / GBP etc. |
| Client présent? | NIF_CLI et NOM_CLI remplis |

---

## 📊 Calculs Automatiques du Système

Une fois importé, le système calcule:

```
Pour chaque article:
  Montant HT = Prix Unitaire × Quantité
  Montant Taxe = Montant HT × Taux Groupe Fiscal
  Montant TTC = Montant HT + Montant Taxe + Montant Taxe Spécifique

Pour la facture complète:
  Total HT = Σ(Montant HT par article)
  Total Taxe = Σ(Montant Taxe par article)
  Total TTC = Total HT + Total Taxe

Exemple (2 articles):
  Article 1: 1000 × 2.5 = 2500 HT → 2500 × 19% = 475 Taxe → 2975 TTC
  Article 2: 50 × 5 = 250 HT → 250 × 13% = 32.50 Taxe → 282.50 TTC
  ───────────────────────────────────────────────────────────────
  TOTAL:     2750 HT → 507.50 Taxe → 3257.50 TTC
```

---

## 🔄 Flux de Normalisation (2 Phases)

```
PHASE 1: Soumission (Submission)
  ├─ Validation syntaxe ✓
  ├─ Calcul des montants ✓
  └─ Génération UID DGI ✓
     Réponse: uid, total, curTotal, vtotal

PHASE 2: Confirmation (Verification)
  ├─ Validation des totaux ✓
  ├─ Génération QR Code ✓
  ├─ Génération Código DEF-DGI ✓
  └─ Enregistrement official ✓
     Réponse: qrCode, dateTime, codeDEFDGI, nim

STATUS FINAL: "CONFIRMED" ✅
```

---

## 📝 Réponse du Système

Après l'import, vous recevez:

```json
{
  "invoiceNumber": "FAC-2025-001",
  "status": "CONFIRMED",
  "isComplete": true,
  "phase1": {
    "uid": "DGI-UUID-XYZ12345",
    "total": 3257.50,
    "curTotal": 3257.50,
    "vtotal": 3257.50,
    "message": "✅ Facture soumise avec succès à la Phase 1"
  },
  "phase2": {
    "qrCode": "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000...",
    "dateTime": "2025-01-15T14:30:00",
    "codeDEFDGI": "DEF-2025-000001",
    "nim": "NIM123456",
    "message": "✅ Facture validée et confirmée par la DGI"
  }
}
```

---

## 🔍 Dépannage

### ❌ Erreur: "NIF invalide"
→ Vérifier que NIF = 8 chiffres exactement

### ❌ Erreur: "Date invalide"
→ Format DD/MM/YYYY requis, pas MM/DD/YYYY

### ❌ Erreur: "Montant incohérent"
→ Vérifier calcul: Prix × Quantité = Montant HT

### ❌ Erreur: "Type facture non reconnu"
→ Utiliser FA, FC ou FE uniquement

### ❌ Erreur: "Groupe fiscal inconnu"
→ Utiliser A, B, E ou X seulement

---

## 📞 Support & Contact

Pour questions/aide:
1. Consulter `INTEGRATION_JSON_API.md`
2. Consulter `EXEMPLE_EXCEL_NORMALISÉ.md`
3. Vérifier les logs du système
4. Contacter: support@middleware.tn

---

## 🎓 Cas d'Usage Complets

### Facture Simple (1 article)
```
FAC-001: 1 × 1000 TND → Tauxo A (19%) → Total: 1190 TND
```

### Facture Multi-Articles (2 articles)
```
FAC-002: 
  - 2.5 h × 1000 = 2500 TND (Groupe A - 19%)
  - 5 u × 50 = 250 TND (Groupe B - 13%)
  Total HT: 2750 TND
  Total Taxe: 475 + 32.50 = 507.50 TND
  Total TTC: 3257.50 TND
```

### Facture d'Avoir (Remboursement)
```
FAC-003 (Type FE):
  - -1 × 500 = -500 TND (remboursement)
  Groupe A (19%): -95 TND
  Total: -595 TND
```

### Facture en Devise Étrangère
```
FAC-004 (EUR):
  - 1 × 2000 EUR (Logiciel)
  - 12 × 200 EUR (Support)
  Total: 2000 + 2400 = 4400 EUR
  (conversion TND au cours du jour appliquée par DGI)
```

---

✅ **Vous êtes prêt!** Utilisez `exemple_factures_normalisees.csv` comme template et adaptez-le à vos besoins.
