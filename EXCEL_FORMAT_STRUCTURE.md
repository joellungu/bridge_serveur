# Format Excel Normalisé - Importation de Factures

## Structure des Colonnes (Une Ligne = Une Facture Complète)

Le fichier Excel doit contenir **23 colonnes** (A à W), où **chaque ligne représente UNE facture complète** avec ses articles.

### Mappage des Colonnes

| Col | Nom (Excel) | Type Java | Obligatoire | Description |
|-----|---|---|---|---|
| **A** | `RN` | String(50) | ✓ | Numéro de Registre (Identifiant facture) |
| **B** | `TYPE` | String(2) | ✓ | Type de facture: `FA` (Facture) / `FC` (Facture Correction) / `FE` (Facture Avoir) |
| **C** | `CLIENT_NIF` | String(13) | ✓ | NIF du client (Numéro d'Immatriculation Fiscal) |
| **D** | `CLIENT_NAME` | String(255) | ✓ | Nom complet du client |
| **E** | `CLIENT_TYPE` | String(2) | ✓ | Type de client: `PE` / `PM` / `PC` / `PL` / `AO` / `PP` |
| **F** | `ITEM_CODE` | String(50) | ✓ | Code article/produit (Premier article de la facture) |
| **G** | `ITEM_NAME` | String(255) | ✓ | Libellé article/produit |
| **H** | `ITEM_PRICE` | Numérique | ✓ | Prix unitaire de l'article (peut être négatif pour avoir) |
| **I** | `ITEM_QUANTITY` | Numérique | ✓ | Quantité (peut être négatif pour avoir) |
| **J** | `ITEM_TAX_GROUP` | String(1) | ✓ | Groupe fiscal: `A` (19%) / `B` (13%) / `E` (7%) / `X` (Exonéré) |
| **K** | `ITEM_ARTICLE_TYPE` | String(1) | ✓ | Nature article: `P` (Produit) / `S` (Service) |
| **L** | `UNIT_PRICE_MODE` | String(3) | ✗ | Mode prix: `ht` (HT) / `ttc` (TTC) - défaut: `ht` |
| **M** | `CURRENCY` | String(3) | ✓ | Code devise ISO (ex: `TND`, `EUR`, `USD`) |
| **N** | `UNIT` | String(20) | ✗ | Unité de mesure (ex: `h`, `u`, `kg`) |
| **O** | `SPECIFIC_TAX_AMOUNT` | Numérique | ✗ | Montant taxe spécifique |
| **P** | `TAX_SPECIFIC_VALUE` | String(10) | ✗ | Valeur taxe spécifique |
| **Q** | `MODE` | String(3) | ✗ | Mode facturation (`ht` ou `ttc`) |
| **R** | `REFERENCE` | String(24) | ✗ | Référence facture originale (si avoir/correction) |
| **S** | `REFERENCE_TYPE` | String(50) | ✗ | Type référence (`FA`, `FC`, etc.) |
| **T** | `REFERENCE_DESC` | String(255) | ✗ | Description de la référence |
| **U** | `CUR_CODE` | String(3) | ✗ | Code devise (si différente de CURRENCY) |
| **V** | `CUR_DATE` | Date/DateTime | ✗ | Date taux de change (format: `DD/MM/YYYY HH:MM:SS`) |
| **W** | `CUR_RATE` | Numérique | ✗ | Taux de change (ex: 3.10 pour EUR) |

---

## Formats de Données

### Dates
- Format: `DD/MM/YYYY` ou `DD/MM/YYYY HH:MM:SS`
- Exemple: `15/01/2025` ou `15/01/2025 14:30:00`

### Valeurs Numériques
- Décimal: `.` comme séparateur (ex: `1250.50`)
- Formatage Excel: Nombre (entier ou décimal accepté)

### Énumérations Valides
- **TYPE (B)**: `FA` | `FC` | `FE`
- **CLIENT_TYPE (E)**: `PE` | `PM` | `PC` | `PL` | `AO` | `PP`
- **ITEM_TAX_GROUP (J)**: `A` | `B` | `E` | `X`
- **ITEM_ARTICLE_TYPE (K)**: `P` | `S`
- **CURRENCY (M) & CUR_CODE (U)**: Codes ISO 3 caractères (ex: `TND`, `EUR`, `USD`, `GBP`)

---

## Exemples Réels

### Exemple 1: Facture Simple (Facture Normale)

```
RN            | TYPE | CLIENT_NIF | CLIENT_NAME           | CLIENT_TYPE | ITEM_CODE | ITEM_NAME                 | ITEM_PRICE | ITEM_QTY | TAX_GRP | ART_TYPE | MODE | CURRENCY | UNIT | ...
FAC-2025-001  | FA   | 11223344   | Entreprise ABC SARL   | PE          | PROD-001  | Consultation Informatique | 1000.00    | 2.5      | A       | S        | ht   | TND      | h    | ...
```

**Interprétation**:
- Facture normale (FA)
- Client: Entreprise ABC SARL (NIF: 11223344)
- 1 article: Consultation de 2.5h à 1000 TND/h
- Groupe fiscal A (19%)
- Service (S)

### Exemple 2: Facture avec Devise Étrangère

```
FAC-2025-004  | FA   | 22334455   | International Trading Inc | PE    | PROD-005  | Licence Software Annuel | 2000.00    | 1        | A       | S        | ht   | EUR      | u    | ... | EUR | 2025-01-18 | 3.10
```

**Interprétation**:
- Facture en EUR avec taux de change 3.10
- 1 article: Licence 2000 EUR/an
- Taux de change: 1 EUR = 3.10 TND

### Exemple 3: Facture d'Avoir (Crédit Note)

```
FAC-2025-003  | FE   | 99001122   | Client Import Export    | PE    | PROD-001  | Avoir Consultation      | -500.00    | -1       | A       | S        | ht   | TND      | h    | ... | FAC-2025-001 | FA | ...
```

**Interprétation**:
- Facture d'avoir (FE)
- Montant négatif (-500.00, -1 quantité)
- Référence la facture originale (FAC-2025-001)

---

## Règles de Validation

### Obligatoires
- ✓ Tous les champs marqués "Obligatoire"
- ✓ RN unique par entreprise
- ✓ Types énumérés valides

### Conditionnels
- Si **TYPE = FE** (Avoir):
  - ITEM_PRICE et ITEM_QUANTITY doivent être **négatifs**
  - REFERENCE doit être **fourni** (facture originale)
  - REFERENCE_TYPE doit être `FA` ou `FC`

- Si **CUR_CODE** ≠ **CURRENCY**:
  - CUR_DATE et CUR_RATE doivent être **fournis**

### Numériques
- `ITEM_PRICE`: Positif pour FA/FC, Négatif pour FE
- `ITEM_QUANTITY`: > 0 pour FA/FC, < 0 pour FE
- `SPECIFIC_TAX_AMOUNT`: ≥ 0
- `CUR_RATE`: > 0

---

## Notes Importantes

⚠️ **Une ligne Excel = Une facture complète**
- Si facture a plusieurs articles: **Un seul article visible par ligne** (le premier)
- Articles supplémentaires: Ajoutés dans la BD, pas visibles en Excel
- Mise à jour Excel: Seul le premier article est mis à jour lors de l'export

⚠️ **Devises**
- CURRENCY (M): Devise facture
- CUR_CODE (U): Devise taux de change (optionnel si identique)
- Si différent: Fournir CUR_DATE et CUR_RATE

⚠️ **Dates**
- Format strict: `DD/MM/YYYY` ou avec heure `DD/MM/YYYY HH:MM:SS`
- Autres dates (emission, paiement, validité): **Défaut système** si non fourni en Excel
