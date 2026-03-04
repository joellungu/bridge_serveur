# Bridge Middleware — Guide d'Intégration API JSON (Facture unitaire & Lot)

> **Version** : 1.0  
> **Date** : 26 février 2026  
> **Audience** : Équipes de développement clientes (FACTS, ERP, POS, etc.)  
> **Contact** : Équipe Middleware  
> **Prérequis** : Avoir un compte Entreprise enregistré et un token DGI configuré

---

## Table des matières

1. [Principe général](#1-principe-général)
2. [Authentification](#2-authentification)
3. [Endpoint — Soumission d'une facture unitaire](#3-endpoint--soumission-dune-facture-unitaire)
   - 3.1 [Requête](#31-requête)
   - 3.2 [Structure de l'objet InvoiceEntity (Body JSON)](#32-structure-de-lobjet-invoiceentity-body-json)
   - 3.3 [Réponse en cas de succès](#33-réponse-en-cas-de-succès)
   - 3.4 [Réponses d'erreur](#34-réponses-derreur)
4. [Endpoint — Soumission d'un lot de factures (Batch)](#4-endpoint--soumission-dun-lot-de-factures-batch)
   - 4.1 [Requête](#41-requête)
   - 4.2 [Structure du body (tableau JSON)](#42-structure-du-body-tableau-json)
   - 4.3 [Réponse batch](#43-réponse-batch)
5. [Endpoint — Consultation d'une facture par UID](#5-endpoint--consultation-dune-facture-par-uid)
6. [Structure complète de l'objet Invoice](#6-structure-complète-de-lobjet-invoice)
   - 6.1 [Champs principaux](#61-champs-principaux)
   - 6.2 [Objet Client](#62-objet-client)
   - 6.3 [Objet Item (article)](#63-objet-item-article)
   - 6.4 [Objet Payment (paiement)](#64-objet-payment-paiement)
   - 6.5 [Objet Operator (opérateur)](#65-objet-operator-opérateur)
   - 6.6 [Champs pour factures d'avoir](#66-champs-pour-factures-davoir)
   - 6.7 [Champs pour devises étrangères](#67-champs-pour-devises-étrangères)
7. [Processus de normalisation DGI (Phase 1 + Phase 2)](#7-processus-de-normalisation-dgi-phase-1--phase-2)
8. [Exemples complets](#8-exemples-complets)
   - 8.1 [Facture de vente simple](#81-facture-de-vente-simple)
   - 8.2 [Facture multi-articles](#82-facture-multi-articles)
   - 8.3 [Facture d'avoir (Credit Note)](#83-facture-davoir-credit-note)
   - 8.4 [Facture en devise étrangère](#84-facture-en-devise-étrangère)
   - 8.5 [Soumission batch (lot)](#85-soumission-batch-lot)
9. [Codes d'erreur](#9-codes-derreur)
10. [FAQ — API JSON](#10-faq--api-json)

---

## 1. Principe général

L'API JSON du Bridge Middleware permet de soumettre des factures à la DGI (Direction Générale des Impôts) pour **normalisation électronique** (e-DEF). Contrairement à l'import Excel (voir *INTEGRATION_DOCUMENTATION.md*), l'API JSON offre :

- ✅ **Factures multi-articles** — Une facture peut contenir plusieurs lignes d'articles dans le tableau `items[]`
- ✅ **Soumission unitaire ou en lot** — Un seul appel pour une ou plusieurs factures
- ✅ **Intégration temps réel** — Réponse JSON immédiate avec le statut DGI
- ✅ **Idempotence** — Une facture déjà confirmée ne sera pas resoumise

```
┌──────────┐   JSON (1 ou N factures)  ┌────────────────┐  par facture   ┌─────────┐
│  Client  │ ─────────────────────────▶│   Middleware    │ ──────────────▶│   DGI   │
│ (FACTS,  │                           │   (Bridge)      │                │  (eDEF) │
│  ERP…)   │◀─────────────────────────│                │◀──────────────│         │
└──────────┘   JSON (résultats)        └────────────────┘  réponse DGI   └─────────┘
```

**Flux de traitement pour chaque facture :**

1. **Validation** — Le Middleware vérifie les champs obligatoires et les formats.
2. **Persistance** — La facture est enregistrée en base de données (statut `PENDING`).
3. **Phase 1 : Soumission** — La facture est envoyée à la DGI → réception d'un **UID** unique.
4. **Phase 2 : Confirmation** — Le Middleware confirme la soumission → réception du **QR Code**, **code DEF**, etc.
5. **Réponse** — Le résultat complet est retourné au client.

---

## 2. Authentification

Toutes les routes de l'API Invoice nécessitent un **token JWT** obtenu via le endpoint de connexion.

### Obtenir un token

```
POST /entreprises/login
Content-Type: application/json
```

**Body :**
```json
{
  "email": "votre-email@entreprise.cd",
  "password": "votre-mot-de-passe"
}
```

**Réponse (200 OK) :**
```json
{
  "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### Utiliser le token

Ajouter le header `Authorization` à **chaque requête** vers `/api/invoice/*` :

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

> ⚠️ Le token expire après **1 an** (configurable). En cas de réponse `401 Unauthorized`, renouveler le token via `/entreprises/login`.

> ⚠️ L'entreprise doit avoir un **token DGI** configuré dans son profil (champ `token` de l'entité Entreprise). Ce token DGI est utilisé par le Middleware pour s'authentifier auprès de l'API e-DEF de la DGI.

---

## 3. Endpoint — Soumission d'une facture unitaire

### 3.1 Requête

```
POST /api/invoice
Authorization: Bearer <token_jwt>
Content-Type: application/json
```

| Paramètre | Détail |
|-----------|--------|
| **Méthode** | `POST` |
| **URL** | `/api/invoice` |
| **Authentification** | JWT Bearer token (rôle `USER` ou `ADMIN`) |
| **Content-Type** | `application/json` |
| **Body** | Un objet JSON représentant une facture (voir §6) |

### 3.2 Structure de l'objet InvoiceEntity (Body JSON)

Le body de la requête est un **objet JSON unique** représentant une facture. Voici les champs **minimaux obligatoires** :

```json
{
  "rn": "FV-2026-00001",
  "type": "FV",
  "mode": "ht",
  "currency": "CDF",
  "client": {
    "nif": "NIF1234567890",
    "name": "Société ABC SARL",
    "type": "PM"
  },
  "items": [
    {
      "code": "ART-001",
      "name": "Consulting informatique",
      "type": "SER",
      "price": 150000.00,
      "quantity": 2,
      "taxGroup": "A"
    }
  ]
}
```

> 📌 **Avantage de l'API JSON vs Excel** : Vous pouvez inclure **plusieurs articles** dans le tableau `items[]` pour une même facture.

### 3.3 Réponse en cas de succès

**HTTP 200 OK** — Facture confirmée par la DGI :

```json
{
  "invoiceNumber": "FV-2026-00001",
  "status": "CONFIRMED",
  "uid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "success": true,
  "message": "✓ Facture validée et confirmée par la DGI",
  "submission": {
    "uid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "total": 300000.00,
    "curTotal": 300000.00,
    "vtotal": 300000.00,
    "status": "PHASE1"
  },
  "confirmation": {
    "qrCode": "00000000000000000000...",
    "dateTime": "2026-02-26T14:30:15",
    "codeDEFDGI": "DEF-2026-456789",
    "counters": "FV:001234",
    "nim": "NIM-XYZ-001",
    "status": "CONFIRMED"
  }
}
```

**Détail des champs de la réponse :**

| Champ | Type | Description |
|-------|------|-------------|
| `invoiceNumber` | String | Le numéro de facture (`rn`) soumis |
| `status` | String | Statut final : `CONFIRMED`, `PHASE1`, ou statut d'erreur |
| `uid` | String (UUID) | Identifiant unique attribué par la DGI |
| `success` | Boolean | `true` si la facture a été traitée sans erreur |
| `message` | String | Message lisible décrivant le résultat |
| `submission` | Object | Données de la Phase 1 (soumission) |
| `submission.uid` | String | UID retourné par la DGI |
| `submission.total` | Number | Montant total calculé par la DGI |
| `submission.curTotal` | Number | Montant total en devise étrangère (si applicable) |
| `submission.vtotal` | Number | Montant total TVA incluse |
| `confirmation` | Object | Données de la Phase 2 (confirmation) — absent si Phase 2 échouée |
| `confirmation.qrCode` | String | QR Code à imprimer sur la facture physique |
| `confirmation.dateTime` | String | Horodatage officiel de validation DGI |
| `confirmation.codeDEFDGI` | String | Numéro officiel e-Invoice (code DEF) |
| `confirmation.counters` | String | Compteurs de la machine émettrice |
| `confirmation.nim` | String | Numéro d'Identification Machine |

### 3.4 Réponses d'erreur

**Facture sans RN (400 Bad Request) :**
```json
{
  "success": false,
  "errorCode": "RN_REQUIRED",
  "errorDescription": "Le numéro de facture (RN) est obligatoire",
  "message": "Une erreur est survenue",
  "timestamp": "2026-02-26T10:30:00"
}
```

**Token invalide ou expiré (401 Unauthorized) :**
```json
{
  "success": false,
  "errorCode": "EMAIL_NOT_FOUND",
  "errorDescription": "Aucun email trouvé dans le token",
  "message": "Une erreur est survenue",
  "timestamp": "2026-02-26T10:30:00"
}
```

**Entreprise introuvable (404 Not Found) :**
```json
{
  "success": false,
  "errorCode": "USER_NOT_FOUND",
  "errorDescription": "Aucun utilisateur trouvé pour cet email",
  "message": "Une erreur est survenue",
  "timestamp": "2026-02-26T10:30:00"
}
```

**Facture déjà confirmée (200 OK) :**
```json
{
  "success": false,
  "errorCode": "INVOICE_ALREADY_CONFIRMED",
  "errorDescription": "Cette facture a déjà été confirmée par la DGI",
  "message": "Une erreur est survenue",
  "timestamp": "2026-02-26T10:30:00"
}
```

**Erreur DGI — Phase 1 échouée :**
```json
{
  "invoiceNumber": "FV-2026-00001",
  "status": "PENDING",
  "uid": null,
  "success": false,
  "message": "✗ Erreur lors du traitement",
  "error": {
    "code": "DGI_PHASE1_ERROR",
    "description": "Numéro de facture déjà utilisé",
    "status": "PENDING"
  }
}
```

**Erreur DGI — Phase 1 OK, Phase 2 échouée :**
```json
{
  "invoiceNumber": "FV-2026-00001",
  "status": "PHASE1",
  "uid": "a1b2c3d4-...",
  "success": true,
  "message": "⏳ Facture soumise avec succès. En attente de confirmation.",
  "submission": {
    "uid": "a1b2c3d4-...",
    "total": 300000.00,
    "curTotal": 300000.00,
    "vtotal": 300000.00,
    "status": "PHASE1"
  },
  "nextStep": {
    "phase": "2",
    "action": "Confirmation de la facture",
    "uid": "a1b2c3d4-..."
  }
}
```

> 💡 Si la réponse contient `status: "PHASE1"` et `nextStep`, cela signifie que la Phase 1 a réussi mais la confirmation DGI a échoué. Vous pouvez resoumettre la même facture (même `rn`) pour retenter la Phase 2 automatiquement.

---

## 4. Endpoint — Soumission d'un lot de factures (Batch)

### 4.1 Requête

```
POST /api/invoice/batch
Authorization: Bearer <token_jwt>
Content-Type: application/json
```

| Paramètre | Détail |
|-----------|--------|
| **Méthode** | `POST` |
| **URL** | `/api/invoice/batch` |
| **Authentification** | JWT Bearer token (rôle `USER` ou `ADMIN`) |
| **Content-Type** | `application/json` |
| **Body** | Un **tableau JSON** d'objets facture |

### 4.2 Structure du body (tableau JSON)

Le body est un **tableau** (`[]`) contenant une ou plusieurs factures. Chaque élément du tableau a la même structure que pour la soumission unitaire :

```json
[
  {
    "rn": "FV-2026-00001",
    "type": "FV",
    "mode": "ht",
    "currency": "CDF",
    "client": { "nif": "NIF1234567890", "name": "Client A", "type": "PM" },
    "items": [
      { "code": "ART-001", "name": "Article 1", "type": "SER", "price": 100000, "quantity": 1, "taxGroup": "A" }
    ]
  },
  {
    "rn": "FV-2026-00002",
    "type": "FV",
    "mode": "ttc",
    "currency": "CDF",
    "client": { "nif": null, "name": "Jean Dupont", "type": "PP" },
    "items": [
      { "code": "ART-002", "name": "Article 2", "type": "BIE", "price": 50000, "quantity": 3, "taxGroup": "A" }
    ]
  }
]
```

### 4.3 Réponse batch

**HTTP 200 OK** — Réponse consolidée du traitement par lot :

```json
{
  "totalSubmitted": 5,
  "totalSuccess": 4,
  "totalFailed": 1,
  "successRate": "80.00%",
  "message": "4 factures traitées avec succès, 1 échecs",
  "success": [
    {
      "invoiceNumber": "FV-2026-00001",
      "status": "CONFIRMED",
      "uid": "a1b2c3d4-...",
      "qrCode": "00000000..."
    },
    {
      "invoiceNumber": "FV-2026-00002",
      "status": "CONFIRMED",
      "uid": "e5f6a7b8-...",
      "qrCode": "11111111..."
    }
  ],
  "failures": [
    {
      "invoiceNumber": "FV-2026-00003",
      "status": "PENDING",
      "uid": null,
      "errorCode": "DGI_PHASE1_ERROR",
      "errorDesc": "NIF client invalide"
    }
  ]
}
```

**Détail des champs de la réponse batch :**

| Champ | Type | Description |
|-------|------|-------------|
| `totalSubmitted` | Number | Nombre total de factures envoyées dans le lot |
| `totalSuccess` | Number | Nombre de factures confirmées avec succès |
| `totalFailed` | Number | Nombre de factures en erreur |
| `successRate` | String | Pourcentage de succès (ex : `"80.00%"`) |
| `message` | String | Résumé lisible du traitement |
| `success` | Array | Liste des factures traitées avec succès |
| `success[].invoiceNumber` | String | Numéro de facture (RN) |
| `success[].status` | String | `CONFIRMED` |
| `success[].uid` | String | UID attribué par la DGI |
| `success[].qrCode` | String | QR Code de la facture |
| `failures` | Array | Liste des factures en échec |
| `failures[].invoiceNumber` | String | Numéro de facture (RN) |
| `failures[].status` | String | Statut d'erreur (`PENDING`, `PHASE1`, etc.) |
| `failures[].errorCode` | String | Code d'erreur technique |
| `failures[].errorDesc` | String | Description de l'erreur |
| `failures[].uid` | String | UID si la Phase 1 avait réussi |
| `failures[].error` | String | Message d'exception (cas d'erreur système) |

**Cas particuliers dans le batch :**

| Situation | Comportement |
|-----------|-------------|
| Une facture a un `rn` null ou vide | Rejetée avec `"error": "RN manquant ou invalide"` dans `failures` |
| Une facture a un `rn` déjà confirmé | Rejetée avec `"error": "Facture déjà confirmée"` + `uid` existant |
| Une facture échoue en Phase 1 | Ajoutée à `failures` avec `errorCode` et `errorDesc` de la DGI |
| Une facture échoue en Phase 2 | Ajoutée à `failures` avec status `PHASE1` (peut être resoumise) |
| Une exception inattendue | Ajoutée à `failures` avec `"error": "Exception: ..."` |

> 📌 **Important** : Le traitement batch est **transactionnel par facture** — l'échec d'une facture n'empêche pas le traitement des suivantes.

**Réponse si le tableau est vide (400 Bad Request) :**
```json
{
  "success": false,
  "errorCode": "EMPTY_BATCH",
  "errorDescription": "La liste de factures est vide",
  "message": "Une erreur est survenue",
  "timestamp": "2026-02-26T10:30:00"
}
```

---

## 5. Endpoint — Consultation d'une facture par UID

Permet de récupérer les détails complets d'une facture déjà soumise.

```
GET /api/invoice/{uid}
Authorization: Bearer <token_jwt>
```

| Paramètre | Détail |
|-----------|--------|
| **Méthode** | `GET` |
| **URL** | `/api/invoice/{uid}` |
| **Path param** | `uid` — L'identifiant unique de la facture (retourné lors de la soumission) |
| **Authentification** | JWT Bearer token (rôle `USER` ou `ADMIN`) |

**Réponse (200 OK) :**

La réponse a la même structure que pour la soumission unitaire (voir §3.3).

**Réponse si facture introuvable (404) :**
```json
{
  "success": false,
  "errorCode": "INVOICE_NOT_FOUND",
  "errorDescription": "Aucune facture trouvée avec ce UID",
  "message": "Une erreur est survenue",
  "timestamp": "2026-02-26T10:30:00"
}
```

> ⚠️ Chaque utilisateur ne peut consulter que ses propres factures (filtrées par email du token JWT).

---

## 6. Structure complète de l'objet Invoice

### 6.1 Champs principaux

| Champ | Type | Obligatoire | Description |
|-------|------|:-----------:|-------------|
| `rn` | String | ✅ Oui | **Numéro de référence de la facture.** Identifiant unique dans votre système. Ne peut pas être réutilisé si déjà confirmé. Format recommandé : `FV-YYYY-NNNNN` pour les factures, `AV-YYYY-NNNNN` pour les avoirs. |
| `type` | String (2 car.) | ✅ Oui | **Type de document fiscal.** `FV` = Facture de Vente, `AV` = Avoir / Credit Note. |
| `mode` | String (2-3 car.) | ✅ Oui | **Mode de calcul des prix.** `ht` = Hors Taxes, `ttc` = Toutes Taxes Comprises. |
| `currency` | String (3 car.) | ✅ Oui | **Code devise ISO 4217.** Devise de facturation. Ex : `CDF`, `USD`, `EUR`. |
| `client` | Object | ✅ Oui | **Informations du client destinataire.** Voir §6.2. |
| `items` | Array\<Item\> | ✅ Oui | **Liste des articles facturés.** Doit contenir au moins 1 élément. Voir §6.3. |
| `issueDate` | String (ISO 8601) | ❌ Non | **Date d'émission.** Format `yyyy-MM-ddTHH:mm:ss`. Si omis, le Middleware utilise la date courante. |
| `dueDate` | String (ISO 8601) | ❌ Non | **Date d'échéance.** Si omis, par défaut +30 jours. |
| `paymentDate` | String (ISO 8601) | ❌ Non | **Date de paiement.** |
| `validityDate` | String (ISO 8601) | ❌ Non | **Date de validité.** |
| `payments` | Array\<Payment\> | ❌ Non | **Moyens de paiement associés.** Voir §6.4. |
| `operator` | Object | ❌ Non | **Opérateur ayant créé la facture.** Si omis, le Middleware utilise les données de l'entreprise. Voir §6.5. |
| `cmta` | String | ❌ Non | **Commentaire A.** Texte libre. |
| `cmtb` | String | ❌ Non | **Commentaire B.** Texte libre. |

### 6.2 Objet Client

| Champ | Type | Obligatoire | Description |
|-------|------|:-----------:|-------------|
| `nif` | String (13 car.) | ⚠️ Conditionnel | **NIF du client.** Obligatoire sauf si `type` = `PP`. Doit commencer par `NIF`. Ex : `NIF1234567890`. |
| `name` | String | ✅ Oui | **Nom ou raison sociale du client.** |
| `type` | String (2 car.) | ✅ Oui | **Catégorie juridique du client.** Valeurs : `PP` (Personne Physique) · `PM` (Personne Morale) · `PC` (Professionnel Commerçant) · `PL` (Professionnel Libéral) · `AO` (Administration/Organisme Public). |
| `contact` | String | ❌ Non | **Contact du client.** Téléphone, email, etc. |
| `address` | String | ❌ Non | **Adresse du client.** |
| `typeDesc` | String | ❌ Non | **Description du type de client.** Si omis, le Middleware génère automatiquement la description (ex : `"Personne Morale"`). |

### 6.3 Objet Item (article)

Chaque facture doit contenir **au moins un item**. L'API JSON permet d'envoyer **plusieurs items** par facture (contrairement à l'import Excel qui est limité à un article par ligne).

| Champ | Type | Obligatoire | Description |
|-------|------|:-----------:|-------------|
| `code` | String | ✅ Oui | **Code de l'article.** Référence interne du produit ou service. Ex : `ART-001`, `SERV-CONSULT`. |
| `name` | String | ✅ Oui | **Désignation de l'article.** Description en clair. Ex : `Consulting informatique`. |
| `type` | String (3 car.) | ✅ Oui | **Nature de l'article.** `SER` = Service · `BIE` = Bien matériel. |
| `price` | Number (décimal) | ✅ Oui | **Prix unitaire.** Doit être strictement > 0. Le mode HT/TTC est déterminé par le champ `mode` de la facture. |
| `quantity` | Number (décimal) | ✅ Oui | **Quantité facturée.** Doit être strictement > 0. Accepte les décimales (ex : `2.5`). |
| `taxGroup` | String (1 car.) | ✅ Oui | **Groupe de taxation DGI.** Code déterminant le taux de taxe. Valeurs : `A` (taux normal 16%) · `B` (taux réduit) · `C` (exonéré) · autres selon réglementation DGI. |
| `taxSpecificAmount` | Number (décimal) | ❌ Non | **Montant de taxe spécifique.** Taxe fixe par unité (ex : taxe carburant). Ajoutée au total en sus de la taxe de groupe. |
| `taxSpecificValue` | String | ❌ Non | **Valeur/code de la taxe spécifique.** Recommandé si `taxSpecificAmount` est renseigné. Ex : `TAXE_CARBURANT`. |
| `originalPrice` | Number (décimal) | ❌ Non | **Prix original avant modification.** Utile en cas de remise. |
| `priceModification` | String | ❌ Non | **Type de modification de prix.** Description de la remise ou ajustement appliqué. |

### 6.4 Objet Payment (paiement)

| Champ | Type | Obligatoire | Description |
|-------|------|:-----------:|-------------|
| `name` | String (20 car. max) | ❌ Non | **Mode de paiement.** Ex : `CASH`, `VIREMENT`, `MOBILE_MONEY`. |
| `amount` | Number (décimal) | ❌ Non | **Montant payé.** |
| `currencyCode` | String (3 car.) | ❌ Non | **Devise du paiement.** Code ISO 4217. |
| `currencyRate` | Number (décimal) | ❌ Non | **Taux de change appliqué.** |

### 6.5 Objet Operator (opérateur)

| Champ | Type | Obligatoire | Description |
|-------|------|:-----------:|-------------|
| `id` | String (UUID) | ❌ Non | **Identifiant de l'opérateur.** |
| `name` | String | ❌ Non | **Nom de l'opérateur.** Personne ou machine ayant émis la facture. |

### 6.6 Champs pour factures d'avoir

> Ces champs sont **obligatoires uniquement si `type` = `AV`** (avoir / credit note).

| Champ | Type | Obligatoire | Description |
|-------|------|:-----------:|-------------|
| `reference` | String (24 car. max) | ⚠️ Requis si AV | **Numéro de la facture originale.** Le `rn` de la facture FV que cet avoir annule. |
| `referenceType` | String | ⚠️ Requis si AV | **Type/motif de l'avoir.** Ex : `CANCEL` (annulation complète), `PARTIAL` (partielle), `CORRECTION`. |
| `referenceDesc` | String | ❌ Non | **Description libre du motif.** Ex : `Annulation suite à résiliation du contrat`. |

### 6.7 Champs pour devises étrangères

> Ces champs sont nécessaires **uniquement pour les factures en devise étrangère**.

| Champ | Type | Obligatoire | Description |
|-------|------|:-----------:|-------------|
| `curCode` | String (3 car.) | ❌ Non | **Code de la devise étrangère.** ISO 4217 (ex : `USD`, `EUR`). Si renseigné, `curDate` et `curRate` deviennent obligatoires. |
| `curDate` | String (ISO 8601) | ⚠️ Si curCode | **Date du taux de change.** Format `yyyy-MM-ddTHH:mm:ss`. |
| `curRate` | Number (décimal) | ⚠️ Si curCode | **Taux de change.** Doit être > 0. Ex : si 1 USD = 2750 CDF, mettre `2750`. |

---

## 7. Processus de normalisation DGI (Phase 1 + Phase 2)

Chaque facture soumise via l'API passe par **deux phases** automatiques auprès de la DGI :

```
┌─────────────┐        ┌──────────────────────────┐        ┌────────────────────────────┐
│   PENDING    │──────▶│      PHASE 1             │──────▶│        PHASE 2              │
│ (Validation) │       │  Soumission à la DGI     │       │  Confirmation à la DGI      │
│              │       │  → Reçoit UID + totaux   │       │  → Reçoit QR Code + DEF     │
└─────────────┘        └──────────────────────────┘        └────────────────────────────┘
       │                        │                                    │
       ▼                        ▼                                    ▼
  ❌ Erreur 400           Status: PHASE1                     Status: CONFIRMED
  (champ manquant)     (en attente de confirmation)        (facture validée ✓)
```

| Phase | Action | Statut résultant | Données reçues |
|-------|--------|:----------------:|----------------|
| **Validation** | Vérification locale des champs obligatoires | — | Erreur 400 si invalide |
| **Phase 1** | Soumission de la facture brute à la DGI | `PHASE1` | `uid`, `total`, `curTotal`, `vtotal` |
| **Phase 2** | Confirmation avec les totaux validés | `CONFIRMED` | `qrCode`, `dateTime`, `codeDEFDGI`, `counters`, `nim` |

**Mécanismes de résilience :**

- Chaque phase bénéficie d'un **retry automatique** (jusqu'à 3 tentatives avec 2 secondes de délai).
- Si la Phase 1 réussit mais la Phase 2 échoue, la facture reste en statut `PHASE1` et peut être resoumise.
- Si vous resoumettez un `rn` en statut `PHASE1`, le Middleware reprend automatiquement à la Phase 2.
- Si vous resoumettez un `rn` en statut `CONFIRMED`, le Middleware retourne `INVOICE_ALREADY_CONFIRMED`.

---

## 8. Exemples complets

### 8.1 Facture de vente simple

**Requête :**
```bash
curl -X POST https://votre-serveur/api/invoice \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..." \
  -H "Content-Type: application/json" \
  -d '{
    "rn": "FV-2026-00001",
    "type": "FV",
    "mode": "ht",
    "currency": "CDF",
    "client": {
      "nif": "NIF1234567890",
      "name": "Société ABC SARL",
      "type": "PM"
    },
    "items": [
      {
        "code": "SERV-001",
        "name": "Consulting informatique",
        "type": "SER",
        "price": 150000.00,
        "quantity": 2,
        "taxGroup": "A"
      }
    ]
  }'
```

**Réponse (200 OK) :**
```json
{
  "invoiceNumber": "FV-2026-00001",
  "status": "CONFIRMED",
  "uid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "success": true,
  "message": "✓ Facture validée et confirmée par la DGI",
  "submission": {
    "uid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "total": 300000.00,
    "curTotal": 300000.00,
    "vtotal": 348000.00,
    "status": "PHASE1"
  },
  "confirmation": {
    "qrCode": "00000000000000000000...",
    "dateTime": "2026-02-26T14:30:15",
    "codeDEFDGI": "DEF-2026-456789",
    "counters": "FV:001234",
    "nim": "NIM-XYZ-001",
    "status": "CONFIRMED"
  }
}
```

### 8.2 Facture multi-articles

```bash
curl -X POST https://votre-serveur/api/invoice \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..." \
  -H "Content-Type: application/json" \
  -d '{
    "rn": "FV-2026-00010",
    "type": "FV",
    "mode": "ht",
    "currency": "CDF",
    "client": {
      "nif": "NIF9876543210",
      "name": "Entreprise XYZ SA",
      "type": "PM"
    },
    "items": [
      {
        "code": "SERV-001",
        "name": "Consulting informatique",
        "type": "SER",
        "price": 150000.00,
        "quantity": 2,
        "taxGroup": "A"
      },
      {
        "code": "ART-005",
        "name": "Câble réseau Cat6 (100m)",
        "type": "BIE",
        "price": 25000.00,
        "quantity": 5,
        "taxGroup": "A"
      },
      {
        "code": "SERV-003",
        "name": "Installation et configuration",
        "type": "SER",
        "price": 75000.00,
        "quantity": 1,
        "taxGroup": "A"
      }
    ],
    "cmta": "Projet réseau bureau principal",
    "cmtb": "Paiement à 30 jours"
  }'
```

> 📌 Cet exemple illustre l'envoi de **3 articles** dans une seule facture, ce qui n'est possible qu'avec l'API JSON (l'import Excel est limité à 1 article par ligne).

### 8.3 Facture d'avoir (Credit Note)

```bash
curl -X POST https://votre-serveur/api/invoice \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..." \
  -H "Content-Type: application/json" \
  -d '{
    "rn": "AV-2026-00001",
    "type": "AV",
    "mode": "ht",
    "currency": "CDF",
    "reference": "FV-2026-00001",
    "referenceType": "CANCEL",
    "referenceDesc": "Annulation suite à résiliation du contrat",
    "client": {
      "nif": "NIF1234567890",
      "name": "Société ABC SARL",
      "type": "PM"
    },
    "items": [
      {
        "code": "SERV-001",
        "name": "Consulting informatique",
        "type": "SER",
        "price": 150000.00,
        "quantity": 2,
        "taxGroup": "A"
      }
    ]
  }'
```

> ⚠️ Pour un avoir (`type: "AV"`), les champs `reference` et `referenceType` sont **obligatoires**. Le champ `reference` doit contenir le `rn` exact de la facture originale confirmée.

### 8.4 Facture en devise étrangère

```bash
curl -X POST https://votre-serveur/api/invoice \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..." \
  -H "Content-Type: application/json" \
  -d '{
    "rn": "FV-2026-00020",
    "type": "FV",
    "mode": "ht",
    "currency": "USD",
    "curCode": "USD",
    "curDate": "2026-02-26T00:00:00",
    "curRate": 2750.00,
    "client": {
      "nif": "NIF9876543210",
      "name": "Client International Ltd",
      "type": "PM"
    },
    "items": [
      {
        "code": "ART-005",
        "name": "Équipement réseau",
        "type": "BIE",
        "price": 500.00,
        "quantity": 3,
        "taxGroup": "B"
      }
    ]
  }'
```

> Le Middleware calcule : total_USD = 500 × 3 = 1 500 USD → total_CDF = 1 500 × 2 750 = 4 125 000 CDF.

### 8.5 Soumission batch (lot)

```bash
curl -X POST https://votre-serveur/api/invoice/batch \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..." \
  -H "Content-Type: application/json" \
  -d '[
    {
      "rn": "FV-2026-00100",
      "type": "FV",
      "mode": "ht",
      "currency": "CDF",
      "client": {
        "nif": "NIF1234567890",
        "name": "Client Alpha",
        "type": "PM"
      },
      "items": [
        { "code": "ART-001", "name": "Fournitures bureau", "type": "BIE", "price": 25000, "quantity": 10, "taxGroup": "A" }
      ]
    },
    {
      "rn": "FV-2026-00101",
      "type": "FV",
      "mode": "ttc",
      "currency": "CDF",
      "client": {
        "name": "Jean Mutombo",
        "type": "PP"
      },
      "items": [
        { "code": "SERV-010", "name": "Réparation PC", "type": "SER", "price": 50000, "quantity": 1, "taxGroup": "A" }
      ]
    },
    {
      "rn": "FV-2026-00102",
      "type": "FV",
      "mode": "ht",
      "currency": "USD",
      "curCode": "USD",
      "curDate": "2026-02-26T00:00:00",
      "curRate": 2750.00,
      "client": {
        "nif": "NIF5555555555",
        "name": "Import-Export SA",
        "type": "PM"
      },
      "items": [
        { "code": "ART-020", "name": "Container 20 pieds", "type": "BIE", "price": 3000, "quantity": 1, "taxGroup": "B" },
        { "code": "SERV-020", "name": "Frais de douane", "type": "SER", "price": 500, "quantity": 1, "taxGroup": "A" }
      ]
    }
  ]'
```

**Réponse :**
```json
{
  "totalSubmitted": 3,
  "totalSuccess": 3,
  "totalFailed": 0,
  "successRate": "100.00%",
  "message": "3 factures traitées avec succès, 0 échecs",
  "success": [
    { "invoiceNumber": "FV-2026-00100", "status": "CONFIRMED", "uid": "...", "qrCode": "..." },
    { "invoiceNumber": "FV-2026-00101", "status": "CONFIRMED", "uid": "...", "qrCode": "..." },
    { "invoiceNumber": "FV-2026-00102", "status": "CONFIRMED", "uid": "...", "qrCode": "..." }
  ],
  "failures": []
}
```

---

## 9. Codes d'erreur

### Erreurs de validation (Middleware)

| Code | HTTP | Description | Action recommandée |
|------|:----:|-------------|-------------------|
| `RN_REQUIRED` | 400 | Le numéro de facture (RN) est manquant ou vide | Ajouter le champ `rn` avec une valeur unique |
| `EMPTY_BATCH` | 400 | Le tableau de factures envoyé est vide | Envoyer au moins une facture dans le tableau |
| `EMAIL_NOT_FOUND` | 401 | Le token JWT ne contient pas d'email | Régénérer le token via `/entreprises/login` |
| `USER_NOT_FOUND` | 404 | Aucune entreprise associée à cet email | Vérifier l'enregistrement de l'entreprise |
| `INVOICE_NOT_FOUND` | 404 | Aucune facture avec ce UID | Vérifier l'UID utilisé |
| `INVOICE_ALREADY_CONFIRMED` | 200 | La facture avec ce RN a déjà été confirmée | Aucune action nécessaire (facture déjà validée) |

### Erreurs DGI

| Code | Phase | Description | Action recommandée |
|------|:-----:|-------------|-------------------|
| `DGI_PHASE1_ERROR` | 1 | Erreur lors de la soumission à la DGI | Vérifier les données de la facture et réessayer |
| `DGI_PHASE2_ERROR` | 2 | Erreur lors de la confirmation | Resoumettre la facture (le Middleware reprendra à la Phase 2) |
| `INVOICE_ALREADY_SUBMITTED` | 1 | La facture a déjà été soumise | Vérifier le statut avec `GET /api/invoice/{uid}` |
| `INVALID_INVOICE_STATUS` | 2 | Le statut de la facture ne permet pas la confirmation | S'assurer que la Phase 1 est complétée |
| `MISSING_UID` | 2 | L'UID de soumission est manquant | Compléter la Phase 1 d'abord |

### Erreurs système

| Code | HTTP | Description | Action recommandée |
|------|:----:|-------------|-------------------|
| `INTERNAL_ERROR` | 500 | Erreur interne du Middleware | Contacter l'équipe Middleware |
| `BATCH_ERROR` | 500 | Erreur interne lors du traitement par lot | Contacter l'équipe Middleware |

---

## 10. FAQ — API JSON

### Q1 : Quelle est la différence entre l'API JSON et l'import Excel ?

| Critère | API JSON | Import Excel |
|---------|----------|-------------|
| Format d'envoi | JSON (application/json) | Fichier `.xlsx` binaire |
| Articles par facture | **Illimité** (tableau `items[]`) | 1 seul article par ligne |
| Réponse | JSON structuré | Fichier `.xlsx` enrichi |
| Cas d'usage | Intégration système, temps réel, multi-articles | Import en masse, migration de données |

---

### Q2 : Puis-je envoyer une facture avec plusieurs articles ?

**Oui**, c'est l'un des avantages principaux de l'API JSON. Ajoutez autant d'éléments que nécessaire dans le tableau `items[]` :

```json
{
  "rn": "FV-2026-00010",
  "items": [
    { "code": "ART-001", "name": "Article 1", "price": 10000, "quantity": 2, "taxGroup": "A", "type": "BIE" },
    { "code": "ART-002", "name": "Article 2", "price": 25000, "quantity": 1, "taxGroup": "A", "type": "SER" },
    { "code": "ART-003", "name": "Article 3", "price": 5000, "quantity": 5, "taxGroup": "B", "type": "BIE" }
  ]
}
```

---

### Q3 : Que se passe-t-il si je ressoumets une facture avec le même RN ?

| Statut actuel | Comportement |
|---------------|-------------|
| `PENDING` ou erreur | La facture existante est retraitée (Phase 1 + Phase 2) |
| `PHASE1` | Le Middleware reprend à la Phase 2 (confirmation) |
| `CONFIRMED` | Rejeté avec `INVOICE_ALREADY_CONFIRMED` |

---

### Q4 : Les champs `nif` et `isf` sont-ils obligatoires dans le body JSON ?

**Non.** Les champs `nif` (NIF de l'entreprise émettrice) et `isf` (ISF de l'entreprise) sont **automatiquement remplis** par le Middleware à partir du profil de l'entreprise connectée (basé sur le token JWT). Vous n'avez pas besoin de les envoyer.

---

### Q5 : Le client de type PP doit-il avoir un NIF ?

**Non.** Pour les clients de type `PP` (Personne Physique), le NIF n'est pas requis. Vous pouvez envoyer `"nif": null` ou omettre le champ. Le `name` reste obligatoire.

```json
{
  "client": {
    "name": "Jean Dupont",
    "type": "PP"
  }
}
```

---

### Q6 : Comment gérer les erreurs partielles dans un batch ?

La réponse batch sépare les résultats en `success` et `failures`. Vous devez :

1. **Traiter les succès** — Stocker les `uid`, `qrCode`, etc. pour chaque facture confirmée.
2. **Analyser les échecs** — Lire le `errorCode` et `errorDesc` de chaque échec.
3. **Corriger et resoumettre** — Corriger les factures en erreur et les resoumettre (unitairement ou en batch).

---

### Q7 : Y a-t-il une limite de factures dans un batch ?

Pas de limite technique stricte, mais pour des performances optimales :

- ✅ **Recommandé** : ≤ 50 factures par batch
- ⚠️ **Acceptable** : 50–200 factures
- ❌ **Déconseillé** : > 200 factures (risque de timeout)

Pour des volumes importants, découpez en plusieurs appels batch.

---

### Q8 : Puis-je mélanger factures (FV) et avoirs (AV) dans un même batch ?

**Oui.** Chaque facture du lot est traitée indépendamment. Assurez-vous que les avoirs ont les champs `reference` et `referenceType` correctement remplis.

---

### Q9 : Comment corriger une facture déjà confirmée ?

La DGI **ne permet pas** la modification d'une facture confirmée. Procédure :

1. Soumettre un **avoir** (`type: "AV"`) référençant la facture originale (`reference: "FV-2026-00001"`).
2. Une fois l'avoir confirmé, soumettre une **nouvelle facture** avec un nouveau `rn`.

---

### Q10 : Quelle est la durée de validité du token JWT ?

Le token JWT a une durée de validité d'**1 an** (365 jours). En cas d'expiration, renouveler via `POST /entreprises/login`.

---

### Q11 : Comment savoir si une facture nécessite une ressoumission ?

Consultez le statut via `GET /api/invoice/{uid}` :

| Statut | Signification | Action |
|--------|---------------|--------|
| `CONFIRMED` | ✅ Facture validée | Aucune action |
| `PHASE1` | ⏳ En attente de confirmation | Resoumettre (même `rn`) pour retenter Phase 2 |
| `PENDING` + erreur | ❌ Échec | Corriger les données et resoumettre |

---

*Document généré pour les équipes d'intégration clientes — volet API JSON (facture unitaire & lot). Pour le volet Import Excel, consulter le fichier `INTEGRATION_DOCUMENTATION.md`. Pour toute question, contacter l'équipe Middleware.*
