# 📊 Exemple de Fichier Excel Normalisé pour le Système

## 📋 Structure et Format

Le système supporte un fichier Excel normalisé avec **une ligne par article de facture**. Chaque ligne contient les informations complètes de la facture + les détails de l'article.

---

## 🏗️ Colonnes Obligatoires (A à O)

| # | Colonne | Type | Format | Exemple | Description |
|---|---------|------|--------|---------|-------------|
| A | **NIF Entreprise** | Texte | 8 caractères | 12345678 | Numéro d'Immatriculation Fiscal de l'entreprise |
| B | **RN Entreprise** | Texte | 8 caractères | 87654321 | Registre National de l'entreprise |
| C | **Numéro Facture** | Texte | - | FAC-2025-001 | Identifiant unique de la facture |
| D | **Type Facture** | Liste | `FA` / `FC` / `FE` | FA | FA=Facture d'Achat, FC=Facture de Correction, FE=Facture d'Avoir |
| E | **Mode Facture** | Liste | `M` / `D` | M | M=Manuel, D=Détail |
| F | **ISF** | Texte | Optionnel | - | Indice de Suivi Fiscal (si applicable) |
| G | **Date Émission** | Date | DD/MM/YYYY | 15/01/2025 | Date de création de la facture |
| H | **Date Paiement** | Date | DD/MM/YYYY | 20/01/2025 | Date du paiement prévu/effectué |
| I | **Date Validité** | Date | DD/MM/YYYY | 15/02/2025 | Date limite de validité |
| **J** | **Devise** | Liste | `TND` / `USD` / `EUR` | TND | Code devise ISO 4217 |

---

## 👥 Informations Client (K à Q)

| # | Colonne | Type | Format | Exemple | Description |
|---|---------|------|--------|---------|-------------|
| K | **NIF Client** | Texte | 8 caractères | 11223344 | NIF du client (acheteur) |
| L | **Nom Client** | Texte | 100 car. max | Entreprise ABC SARL | Raison sociale du client |
| M | **Contact Client** | Texte | Téléphone | +216 20 123456 | Numéro de contact |
| N | **Adresse Client** | Texte | 255 car. max | Rue de la Paix, Tunis | Adresse physique du client |
| O | **Type Client** | Liste | `PE` / `PP` | PE | PE=Personne Exp, PP=Personne Phys |

---

## 📦 Détails Article/Produit (P à W)

| # | Colonne | Type | Format | Exemple | Description |
|---|---------|------|--------|---------|-------------|
| P | **Code Article** | Texte | - | PROD-001 | Code produit unique |
| Q | **Nom Article** | Texte | 255 car. max | Conseil Informatique | Description du produit/service |
| R | **Type Article** | Liste | `P` / `S` | S | P=Produit, S=Service |
| S | **Quantité** | Nombre | Décimal | 2.5 | Nombre d'unités vendues |
| T | **Unité** | Texte | `u` / `kg` / `l` / `h` | h | Unité de mesure (u=unité, kg, l, h=heures) |
| U | **Prix Unitaire** | Nombre | Décimal (2 chiffres) | 1000.00 | Prix hors taxe par unité |
| V | **Groupe Fiscal** | Liste | `A` / `B` / `E` / `X` | A | A=19%, B=13%, E=7%, X=Exonéré |
| W | **Valeur Taxe Spécifique** | Nombre | Optionnel | 0.00 | Montant supplémentaire (si applicable) |

---

## 💳 Paiement (X à Z)

| # | Colonne | Type | Format | Exemple | Description |
|---|---------|------|--------|---------|-------------|
| X | **Mode Paiement** | Liste | `ESP` / `CHQ` / `VIR` / `CB` | VIR | ESP=Espèces, CHQ=Chèque, VIR=Virement, CB=Carte |
| Y | **Montant Paiement** | Nombre | Décimal | 2500.00 | Montant payé (TND) |
| Z | **Devise Paiement** | Texte | Code ISO | TND | Code devise du paiement |

---

## 📝 Exemple Complet d'Excel (3 Factures)

### **Feuille: Factures**

```
NIF_ENT | RN_ENT   | NUM_FAC      | TYPE | MODE | ISF | DATE_EMIS   | DATE_PAIE   | DATE_VALID  | DEVISE | NIF_CLI   | NOM_CLI              | CONTACT_CLI    | ADRESSE_CLI               | TYPE_CLI | CODE_ART  | NOM_ART              | TYPE_ART | QTY | UNIT | PRIX_UNIT | GRP_FISCAL | MONTANT_TAX | MODE_PAYE | MONTANT_PAYE | DEVISE_PAYE
12345678 | 87654321 | FAC-2025-001 | FA   | M    |     | 15/01/2025  | 20/01/2025  | 15/02/2025  | TND    | 11223344  | Entreprise ABC SARL  | +216 20 12345 | Rue de la Paix, Tunis     | PE       | PROD-001  | Conseil Informatique | S        | 2.5 | h    | 1000.00   | A          | 0.00        | VIR       | 2500.00      | TND
12345678 | 87654321 | FAC-2025-001 | FA   | M    |     | 15/01/2025  | 20/01/2025  | 15/02/2025  | TND    | 11223344  | Entreprise ABC SARL  | +216 20 12345 | Rue de la Paix, Tunis     | PE       | PROD-002  | Fournitures Bureau   | P        | 5   | u    | 50.00     | B          | 0.00        | VIR       | 250.00       | TND
12345678 | 87654321 | FAC-2025-002 | FA   | M    |     | 16/01/2025  | 21/01/2025  | 16/02/2025  | TND    | 55667788  | Société XYZ Ltd      | +216 30 98765 | Avenue du Commerce, Sfax  | PE       | PROD-003  | Maintenance Serveur  | S        | 1   | u    | 5000.00   | A          | 0.00        | CHQ       | 5000.00      | TND
12345678 | 87654321 | FAC-2025-003 | FE   | M    |     | 17/01/2025  | 22/01/2025  | 17/02/2025  | TND    | 99001122  | Client Import Export | +216 71 55555 | Boulevard Habib Bourguiba | PE       | PROD-001  | Conseil (Avoir)      | S        | -1  | h    | 500.00    | A          | 0.00        | ESP       | 500.00       | TND
```

---

## 🔍 Règles de Normalisation Appliquées Automatiquement

| Règle | Description | Exemple |
|-------|-------------|---------|
| **Calcul HT** | Prix Unit × Quantité | 1000 × 2.5 = **2500.00** |
| **Calcul Taxe** | HT × Taux Groupe Fiscal | 2500 × 19% (Groupe A) = **475.00** |
| **Calcul TTC** | HT + Taxe + Taxe Spécifique | 2500 + 475 + 0 = **2975.00** |
| **Total Facture** | Σ(TTC par article) | Article1 + Article2 = **2975.00 + 325.00 = 3300.00** |
| **Dates Formatées** | DD/MM/YYYY → LocalDateTime ISO | 15/01/2025 → 2025-01-15T00:00:00 |
| **Groupes Fiscaux** | Codes traduits en pourcentages | A→19%, B→13%, E→7%, X→0% |
| **Deduplication** | Une facture = Plusieurs articles dans 1 ligne conceptuelle | Même NUM_FAC avec codes articles différents |

---

## 🎯 Points Clés

✅ **Une ligne = Un article d'une facture**  
✅ **Même N° de facture = Articles d'une même facture**  
✅ **Colonnes NIF/RN/Dates IDENTIQUES pour tous les articles d'une même facture**  
✅ **Facilite le groupement automatique par N° de facture**  
✅ **Support de 3 types de factures**: FA (Achat), FC (Correction), FE (Avoir)  
✅ **Décompte automatique des montants HT, Taxe, TTC**  

---

## 📥 Comment Importer

1. **Enregistrer en format Excel** : `factures_2025.xlsx`
2. **Feuille unique** : `Factures`
3. **Pas de lignes vides** entre les données
4. **Respecter les formats de date** : DD/MM/YYYY
5. **Respecter les codes types** : FA, FC, FE, M, D, A, B, E, X, etc.

---

## ⚠️ Erreurs Courantes

❌ Dates au format MM/DD/YYYY (format US)  
❌ NIF/RN avec espaces ou tirets  
❌ Montants avec symbole devise (€, $, TND)  
❌ Articles sur plusieurs lignes pour une même facture  
❌ Colonnes manquantes ou mal nommées  

---

## 📱 Résultat Après Normalisation

Le système transforme ces lignes Excel en:

```json
{
  "invoiceNumber": "FAC-2025-001",
  "status": "CONFIRMED",
  "phase1": {
    "uid": "DGI-UUID-12345",
    "total": 3300.00,
    "message": "✅ Facture soumise"
  },
  "phase2": {
    "qrCode": "00000000000000000000...",
    "codeDEFDGI": "DEF-2025-001",
    "message": "✅ Validée par DGI"
  }
}
```

---

## 📞 Support

Pour toute question sur le format ou l'import, consultez:
- `INTEGRATION_JSON_API.md` — Détails API JSON
- `INTEGRATION_DOCUMENTATION.md` — Guide d'import complet
- `INVOICE_SUBMISSION_GUIDE.md` — Processus deux phases DGI
