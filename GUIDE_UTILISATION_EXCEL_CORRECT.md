# Guide d'Utilisation Excel - Importation de Factures

## 📋 Contexte

Ce guide explicite comment préparer et importer des factures en format Excel dans le système de gestion des factures Quarkus.

### 🎯 Concept Principal

**Une ligne Excel = Une facture complète** contenant le premier article de la facture (si plusieurs articles, les autres sont stockés en BD, pas visibles en Excel).

---

## 🏗️ Structure Excel

### Colonnes (23 au total: A-W)

Le fichier Excel **doit impérativement contenir exactement 23 colonnes**:

```
A: RN                    (Numéro Registre/Numéro Facture)
B: TYPE                  (FA, FC ou FE)
C: CLIENT_NIF            (NIF Client)
D: CLIENT_NAME           (Nom Client)
E: CLIENT_TYPE           (PE, PM, PC, PL, AO, PP)
F: ITEM_CODE             (Code Article)
G: ITEM_NAME             (Libellé Article)
H: ITEM_PRICE            (Prix Unitaire)
I: ITEM_QUANTITY         (Quantité)
J: ITEM_TAX_GROUP        (A, B, E, X)
K: ITEM_ARTICLE_TYPE     (P ou S)
L: UNIT_PRICE_MODE       (ht ou ttc)
M: CURRENCY              (TND, EUR, USD, ...)
N: UNIT                  (h, u, kg, ...)
O: SPECIFIC_TAX_AMOUNT   (Montant Taxe Spécifique)
P: TAX_SPECIFIC_VALUE    (Valeur Taxe Spécifique)
Q: MODE                  (ht ou ttc)
R: REFERENCE             (Pour factures d'avoir)
S: REFERENCE_TYPE        (FA ou FC)
T: REFERENCE_DESC        (Description Référence)
U: CUR_CODE              (Code Devise - si différent)
V: CUR_DATE              (Date Taux Change)
W: CUR_RATE              (Taux Change)
```

---

## 📝 Procédure d'Import

### Étape 1: Créer le fichier Excel

Ouvrir Excel ou LibreOffice Calc et créer un fichier avec les colonnes A-W.

**En-tête obligatoire (ligne 1)**:
```
RN,TYPE,CLIENT_NIF,CLIENT_NAME,CLIENT_TYPE,ITEM_CODE,ITEM_NAME,ITEM_PRICE,ITEM_QUANTITY,ITEM_TAX_GROUP,ITEM_ARTICLE_TYPE,UNIT_PRICE_MODE,CURRENCY,UNIT,SPECIFIC_TAX_AMOUNT,TAX_SPECIFIC_VALUE,MODE,REFERENCE,REFERENCE_TYPE,REFERENCE_DESC,CUR_CODE,CUR_DATE,CUR_RATE
```

### Étape 2: Remplir les données

**Exemple valide**:
```
FAC-2025-001,FA,11223344,Entreprise ABC,PE,PROD-001,Consultation,1000.00,2.5,A,S,ht,TND,h,0.00,,ht,,,,,
```

**Décodage**:
- A: FAC-2025-001 (numéro facture)
- B: FA (facture normale)
- C: 11223344 (NIF client)
- D: Entreprise ABC (nom client)
- E: PE (type client = Personne Exploitante)
- F: PROD-001 (code article)
- G: Consultation (libellé)
- H: 1000.00 (prix 1000 TND)
- I: 2.5 (2.5 unités)
- J: A (groupe fiscal 19%)
- K: S (type = Service)
- L: ht (prix hors-taxe)
- M: TND (devise Tunisienne)
- N: h (heure)
- O: 0.00 (pas de taxe spécifique)
- P: (vide)
- Q: ht (mode HT)
- R-W: (vides = non utilisés)

### Étape 3: Sauvegarder le fichier

Sauvegarder en format **CSV (UTF-8)** ou **XLSX (Excel)**.

### Étape 4: Importer via API

**Endpoint**: `POST /invoices/upload-excel`

**Headers**:
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: multipart/form-data
```

**Paramètres**:
```
file: <fichier.csv ou fichier.xlsx>
```

**Response (200 OK)**:
```json
{
  "message": "4 factures importées avec succès",
  "successful": 4,
  "failed": 0,
  "details": [
    {
      "rn": "FAC-2025-001",
      "status": "PENDING",
      "message": "Facture créée"
    }
  ]
}
```

### Étape 5: Vérifier les résultats

Consulter l'API `/invoices` pour vérifier les factures importées.

---

## ✅ Formats Acceptés

### Dates
- **Format strict**: `DD/MM/YYYY`
- **Avec heure**: `DD/MM/YYYY HH:MM:SS`
- **Exemples**: `15/01/2025` ou `15/01/2025 14:30:00`

### Valeurs Numériques
- **Séparateur décimal**: `.` (point)
- **Aucun séparateur de milliers** en CSV
- **Exemples**: `1250.50`, `100`, `0.99`

### Énumérations Valides

| Champ | Valeurs Valides | Exemple |
|-------|--|--|
| TYPE (B) | FA, FC, FE | FA = Facture normale |
| CLIENT_TYPE (E) | PE, PM, PC, PL, AO, PP | PE = Personne Exploitante |
| ITEM_TAX_GROUP (J) | A, B, E, X | A = 19%, B = 13%, E = 7%, X = Exonéré |
| ITEM_ARTICLE_TYPE (K) | P, S | P = Produit, S = Service |
| CURRENCY (M, U) | ISO 3 letters | TND, EUR, USD, GBP, CHF, etc. |

---

## 🚨 Règles de Validation Strictes

### Champs Obligatoires

**TOUJOURS requis**:
- A: RN (non-vide)
- B: TYPE (doit être FA, FC ou FE)
- C: CLIENT_NIF (non-vide)
- D: CLIENT_NAME (non-vide)
- E: CLIENT_TYPE (valide: PE/PM/PC/PL/AO/PP)
- F: ITEM_CODE (non-vide)
- G: ITEM_NAME (non-vide)
- H: ITEM_PRICE (numérique)
- I: ITEM_QUANTITY (numérique, non-zéro sauf FE)
- J: ITEM_TAX_GROUP (valide: A/B/E/X)
- K: ITEM_ARTICLE_TYPE (valide: P/S)
- M: CURRENCY (valide ISO)

### Règles de Validation par Type

#### FA - Facture Normale ✓
- ITEM_PRICE > 0 (positif)
- ITEM_QUANTITY > 0 (positif)
- REFERENCE, REFERENCE_TYPE: vides ou optionnels

#### FC - Facture Correction ✓
- ITEM_PRICE > 0 (positif)
- ITEM_QUANTITY > 0 (positif)
- Peut référencer facture originale (optionnel)

#### FE - Facture d'Avoir ⚠️
- **ITEM_PRICE < 0** (NÉGATIF - obligatoire)
- **ITEM_QUANTITY < 0** (NÉGATIF - obligatoire)
- **REFERENCE** fourni (obligatoire - numéro facture originale)
- **REFERENCE_TYPE** = FA ou FC (obligatoire)

### Devises

- **CURRENCY (M)**: Devise principale (ex: TND)
- **CUR_CODE (U)**: Devise alternative (optionnel)
  - Si fourni ≠ CURRENCY:
    - **CUR_DATE** obligatoire (date taux)
    - **CUR_RATE** obligatoire (ex: 3.10 pour EUR)

### Montants

- **SPECIFIC_TAX_AMOUNT (O)**: ≥ 0 (zéro ou positif)
- **CUR_RATE (W)**: > 0 (strictement positif)

---

## ❌ Erreurs Courantes et Solutions

| Erreur | Cause | Solution |
|--------|-------|----------|
| "RN (A) manquant" | Cellule A vide | Ajouter numéro facture (ex: FAC-001) |
| "TYPE (B) invalide" | Valeur ≠ FA/FC/FE | Corriger TYPE à FA, FC ou FE |
| "CLIENT_TYPE (E) invalide" | Valeur inconnue | Utiliser PE, PM, PC, PL, AO ou PP |
| "ITEM_TAX_GROUP (J) invalide" | Valeur ≠ A/B/E/X | Corriger à A, B, E ou X |
| "ITEM_QUANTITY (I) zéro" | Valeur = 0 | Mettre quantité > 0 (ou < 0 si FE) |
| "ITEM_PRICE négatif" | Prix < 0 (hors FE) | Pour FA/FC: prix doit être ≥ 0 |
| "Date format invalide" | Format ≠ DD/MM/YYYY | Format: DD/MM/YYYY ou DD/MM/YYYY HH:MM:SS |
| "CURRENCY invalide" | Code ≠ ISO 3 lettres | Utiliser code ISO (TND, EUR, USD, etc.) |
| "FE sans REFERENCE" | Facture d'avoir vide | Pour FE: fournir numéro facture originale |

---

## 💾 Export Excel

### Récupérer le fichier Excel mis à jour

Après import, télécharger le fichier Excel avec statuts mis à jour:

**Endpoint**: `GET /invoices/export-excel`

**Response**:
- Fichier Excel avec colonnes A-W
- Colonnese additionnelles (X-AC):
  - X: ERROR_CODE (si erreur)
  - Y: ERROR_DESC (message d'erreur)
  - Z: DATETIME (timestamp)
  - AA: QR_CODE (code QR DGI)
  - AB: DGI_CODE (code DGI)
  - AC: COUNTERS (compteurs)
  - AD: NIM (NIM)

---

## 📊 Exemples Complets

### Exemple 1: Facture Normale (FA)

**Données**:
- Facture: FAC-2025-001
- Client: Entreprise ABC (NIF: 11223344)
- Article: Consultation 2.5h à 1000 TND/h
- Taxe: 19% (Groupe A)

**CSV**:
```csv
RN,TYPE,CLIENT_NIF,CLIENT_NAME,CLIENT_TYPE,ITEM_CODE,ITEM_NAME,ITEM_PRICE,ITEM_QUANTITY,ITEM_TAX_GROUP,ITEM_ARTICLE_TYPE,UNIT_PRICE_MODE,CURRENCY,UNIT,SPECIFIC_TAX_AMOUNT,TAX_SPECIFIC_VALUE,MODE,REFERENCE,REFERENCE_TYPE,REFERENCE_DESC,CUR_CODE,CUR_DATE,CUR_RATE
FAC-2025-001,FA,11223344,Entreprise ABC,PE,PROD-001,Consultation,1000.00,2.5,A,S,ht,TND,h,0.00,,ht,,,,,
```

**Résultat BD**:
```
InvoiceEntity {
  rn: "FAC-2025-001"
  type: "FA"
  client: { nif: "11223344", name: "Entreprise ABC", type: "PE", typeDesc: "Personne Exploitante" }
  items[0]: { code: "PROD-001", name: "Consultation", price: 1000.00, quantity: 2.5, unit: "h", taxGroup: "A", type: "S" }
  currency: "TND"
  mode: "ht"
  status: "PENDING"
}
```

### Exemple 2: Facture d'Avoir (FE)

**Données**:
- Facture avoir: FAC-2025-003
- Référence: FAC-2025-001
- Client: Client Import Export (NIF: 99001122)
- Avoir: -500 TND (1h consultaion)

**CSV**:
```csv
FAC-2025-003,FE,99001122,Client Import Export,PE,PROD-001,Avoir Consultation,-500.00,-1,A,S,ht,TND,h,0.00,,ht,FAC-2025-001,FA,Retour Consultatant,,,
```

**Points critiques**:
- ✓ TYPE = FE
- ✓ ITEM_PRICE = -500.00 (négatif)
- ✓ ITEM_QUANTITY = -1 (négatif)
- ✓ REFERENCE = FAC-2025-001 (facture originale)
- ✓ REFERENCE_TYPE = FA

### Exemple 3: Facture en Devise Étrangère

**Données**:
- Facture: FAC-2025-004
- Client: International Trading Inc
- Devise: EUR à 3.10 TND/EUR
- Licence: 2000 EUR/an

**CSV**:
```csv
FAC-2025-004,FA,22334455,International Trading Inc,PE,PROD-005,Licence Software Annuel,2000.00,1,A,S,ht,EUR,u,0.00,,ht,,,,,EUR,2025-01-18T00:00:00,3.10
```

**Points critiques**:
- ✓ CURRENCY = EUR (devise principale)
- ✓ CUR_CODE = EUR (pas différent, donc optionnel mais fourni)
- ✓ CUR_DATE = 2025-01-18 (date taux)
- ✓ CUR_RATE = 3.10 (taux EUR/TND)

---

## 🔧 Troubleshooting

### Import échoue complètement

**Cause possible**: Format ou encodage du fichier incorrect

**Solutions**:
1. Vérifier que le fichier est en **UTF-8**
2. Vérifier **23 colonnes exactement** (A-W)
3. Vérifier **en-tête correct** (première ligne)
4. Tester avec fichier CSV plutôt que XLSX
5. Vérifier pas d'espaces inutiles avant/après données

### Certaines lignes échouent

**Cause possible**: Valeurs invalides dans une ou plusieurs colonnes

**Solutions**:
1. Vérifier énumérations (TYPE, CLIENT_TYPE, ITEM_TAX_GROUP, ITEM_ARTICLE_TYPE)
2. Vérifier formats de données (dates DD/MM/YYYY, montants avec `.`)
3. Vérifier montants négatifs pour FE
4. Vérifier REFERENCE fourni pour FE

### Facture créée mais statut n'est pas correct

**Cause possible**: Validation DGI non encore exécutée

**Solutions**:
1. Vérifier avec `GET /invoices/{id}` le statut
2. Attendre traitement asynchrone DGI
3. Consulter `dgiResponse` pour détails

---

## 📞 Support

Pour questions ou problèmes:
1. Consulter les logs d'erreur dans le fichier Excel exporté (colonnes X-Y)
2. Vérifier que l'utilisateur a les droits (role: `user`)
3. Contacter support avec:
   - Fichier Excel original
   - Erreurs reçues
   - Logs API (`/var/log/quarkus.log`)
