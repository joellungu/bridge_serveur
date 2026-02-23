# Bridge Middleware — Guide d'Intégration Excel (FACTS ↔ DGI)

> **Version** : 1.0  
> **Date** : 23 février 2026  
> **Audience** : Équipes de développement FACTS  
> **Contact** : Équipe Middleware

---

## Table des matières

1. [Principe général](#1-principe-général)
2. [Endpoint d'import Excel](#2-endpoint-dimport-excel)
3. [Colonnes d'entrée — Ce que FACTS doit envoyer](#3-colonnes-dentrée--ce-que-facts-doit-envoyer)
   - 3.1 [Informations facture](#31-informations-facture)
   - 3.2 [Informations client](#32-informations-client)
   - 3.3 [Informations article (ligne de facture)](#33-informations-article-ligne-de-facture)
   - 3.4 [Informations fiscales et taxe spécifique](#34-informations-fiscales-et-taxe-spécifique)
   - 3.5 [Champs pour factures d'avoir (Credit Note)](#35-champs-pour-factures-davoir-credit-note)
   - 3.6 [Champs pour devises étrangères](#36-champs-pour-devises-étrangères)
4. [Colonnes de sortie — Ce que FACTS reçoit en retour](#4-colonnes-de-sortie--ce-que-facts-reçoit-en-retour)
   - 4.1 [Champs de succès (remplis par la DGI)](#41-champs-de-succès-remplis-par-la-dgi)
   - 4.2 [Champs d'erreur](#42-champs-derreur)
5. [Tableau récapitulatif complet des colonnes](#5-tableau-récapitulatif-complet-des-colonnes)
6. [Règles de validation appliquées par le Middleware](#6-règles-de-validation-appliquées-par-le-middleware)
7. [Exemples de fichiers Excel](#7-exemples-de-fichiers-excel)
   - 7.1 [Facture standard](#71-facture-standard)
   - 7.2 [Facture d'avoir (Credit Note)](#72-facture-davoir-credit-note)
   - 7.3 [Facture en devise étrangère](#73-facture-en-devise-étrangère)
   - 7.4 [Fichier Excel retourné après traitement](#74-fichier-excel-retourné-après-traitement)
8. [Réponses HTTP possibles](#8-réponses-http-possibles)
9. [FAQ Excel](#9-faq-excel)

---

## 1. Principe général

Le Bridge Middleware permet à FACTS d'envoyer un fichier Excel (`.xlsx`) contenant une ou plusieurs factures. Le Middleware :

```
┌─────────┐   fichier .xlsx    ┌────────────────┐   par facture   ┌─────────┐
│  FACTS  │ ──────────────────▶│   Middleware    │ ───────────────▶│   DGI   │
│         │                    │   (Bridge)      │                 │  (eDEF) │
│         │◀──────────────────│                │◀───────────────│         │
└─────────┘   fichier .xlsx    └────────────────┘   réponse DGI   └─────────┘
              enrichi (résultats)
```

1. **Lit** chaque ligne du fichier Excel (en sautant l'en-tête ligne 1).
2. **Valide** les champs obligatoires et les formats.
3. **Crée** une facture en base de données pour chaque ligne valide.
4. **Soumet** chaque facture à la DGI en deux phases (soumission + confirmation).
5. **Écrit les résultats** dans les colonnes de sortie (X à AD) du fichier Excel.
6. **Retourne** le fichier `.xlsx` enrichi à FACTS.

---

## 2. Endpoint d'import Excel

```
POST /api/invoice/upload-excel
Authorization: Bearer <token_jwt>
Content-Type: application/octet-stream
Body: <contenu binaire du fichier .xlsx>
```

| Paramètre | Détail |
|-----------|--------|
| **Méthode** | `POST` |
| **URL** | `/api/invoice/upload-excel` |
| **Authentification** | JWT Bearer token (obtenu via `POST /entreprises/login`) |
| **Content-Type** | `application/octet-stream` |
| **Body** | Le fichier `.xlsx` en binaire brut |
| **Format du fichier** | Microsoft Excel `.xlsx` (OOXML), 1 seule feuille, ligne 1 = en-tête |

---

## 3. Colonnes d'entrée — Ce que FACTS doit envoyer

La **première ligne** du fichier Excel est l'en-tête (elle est ignorée par le Middleware).  
Chaque ligne à partir de la **ligne 2** représente une facture.

---

### 3.1 Informations facture

| Col. | Lettre | Nom de l'en-tête | Type | Obligatoire | Description détaillée |
|:----:|:------:|-------------------|------|:-----------:|----------------------|
| 0 | **A** | `rn` | Texte | ✅ Oui | **Numéro de référence de la facture.** Identifiant unique de la facture dans FACTS. Ce numéro sera transmis à la DGI et servira d'identifiant universel du document. Il **ne peut pas être réutilisé** : si un RN déjà confirmé est renvoyé, le Middleware retourne une erreur. Format recommandé : `FV-YYYY-NNNNN` pour les factures, `AV-YYYY-NNNNN` pour les avoirs. |
| 1 | **B** | `type` | Texte (2 car.) | ✅ Oui | **Type de document fiscal.** Détermine la nature du document envoyé à la DGI. Valeurs acceptées : `FV` = Facture de Vente (document standard de facturation), `AV` = Avoir / Credit Note (document d'annulation ou de remboursement partiel/total — doit obligatoirement référencer une facture originale via les colonnes R, S, T). |
| 16 | **Q** | `mode` | Texte ou Nombre | ❌ Non | **Mode de calcul des prix.** Indique si les prix unitaires des articles (colonne H) sont exprimés hors taxes ou toutes taxes comprises. Valeurs : `ht` ou `0` = Hors Taxes (les taxes seront calculées en sus du prix), `ttc` ou `1` = Toutes Taxes Comprises (les taxes sont incluses dans le prix). Si omis, le Middleware utilise la valeur de la colonne L. |
| 12 | **M** | `currency` | Texte (3 car.) | ✅ Oui | **Code devise de la facture.** Code ISO 4217 de la devise dans laquelle la facture est libellée. Exemples : `CDF` = Franc Congolais, `USD` = Dollar Américain, `EUR` = Euro. C'est la devise principale de facturation. Pour les opérations en devise étrangère, voir les colonnes U, V, W. |

---

### 3.2 Informations client

| Col. | Lettre | Nom de l'en-tête | Type | Obligatoire | Description détaillée |
|:----:|:------:|-------------------|------|:-----------:|----------------------|
| 2 | **C** | `clientNif` | Texte (13 car.) | ⚠️ Conditionnel | **Numéro d'Identification Fiscale du client.** Identifiant fiscal unique attribué par la DGI au client destinataire de la facture. **Doit commencer par `NIF`** (ex : `NIF1234567890`). Obligatoire pour tous les types de clients **sauf `PP`** (Personne Physique). Pour les clients PP sans NIF, laisser cette cellule vide. |
| 3 | **D** | `clientName` | Texte | ✅ Oui | **Nom ou raison sociale du client.** Nom complet de la personne physique ou dénomination sociale de l'entreprise/organisme destinataire de la facture. Ce nom sera imprimé sur la facture électronique validée par la DGI. |
| 4 | **E** | `clientType` | Texte (2 car.) | ✅ Oui | **Catégorie juridique du client.** Détermine les règles de validation appliquées (notamment l'obligation du NIF). Valeurs acceptées : `PP` = Personne Physique (particulier, pas de NIF requis) · `PM` = Personne Morale (entreprise, société — NIF obligatoire) · `PC` = Professionnel Commerçant (commerçant enregistré — NIF obligatoire) · `PL` = Professionnel Libéral (médecin, avocat, etc. — NIF obligatoire) · `AO` = Administration ou Organisme Public (entité étatique — NIF obligatoire). |

---

### 3.3 Informations article (ligne de facture)

> **Note** : Chaque ligne Excel représente une facture avec **un seul article**. Si une facture a plusieurs articles, créer une ligne par article avec le même `rn`, ou utiliser l'API JSON pour les factures multi-articles.

| Col. | Lettre | Nom de l'en-tête | Type | Obligatoire | Description détaillée |
|:----:|:------:|-------------------|------|:-----------:|----------------------|
| 5 | **F** | `itemCode` | Texte | ✅ Oui | **Code de l'article ou du service.** Référence interne utilisée par FACTS pour identifier l'article ou la prestation facturée. Ce code est transmis à la DGI et doit correspondre au catalogue de produits/services de l'entreprise. Exemple : `ART-001`, `SERV-CONSULT`. |
| 6 | **G** | `itemName` | Texte | ✅ Oui | **Désignation de l'article ou du service.** Description en clair de la prestation ou du produit facturé. Ce texte apparaîtra sur la facture électronique validée. Exemple : `Consulting informatique`, `Fournitures de bureau`. |
| 7 | **H** | `itemPrice` | Nombre décimal | ✅ Oui | **Prix unitaire de l'article.** Montant par unité, exprimé dans la devise de la facture (colonne M). Doit être **strictement supérieur à 0**. Le mode HT/TTC est déterminé par les colonnes L ou Q. Exemple : `150000.00`. |
| 8 | **I** | `itemQuantity` | Nombre décimal | ✅ Oui | **Quantité facturée.** Nombre d'unités de l'article ou du service. Doit être **strictement supérieur à 0**. Accepte les décimales pour les unités de mesure fractionnables (ex : 2.5 kg). Exemple : `1`, `2.5`, `10`. |
| 9 | **J** | `itemTaxGroup` | Texte (1 car.) | ✅ Oui | **Groupe de taxation applicable.** Code défini par la DGI qui détermine le taux de taxe appliqué à cet article. Le taux réel est calculé côté DGI selon le groupe. Valeurs courantes : `A` = Taux normal (16%) · `B` = Taux réduit · `C` = Exonéré · Autres codes selon la réglementation DGI en vigueur. |
| 10 | **K** | `itemArticleType` | Texte (3 car.) | ✅ Oui | **Nature de l'article.** Distingue un bien matériel d'une prestation de service. Cette distinction a un impact fiscal selon la réglementation DGI. Valeurs : `SER` = Service (prestation immatérielle : consulting, formation, location, etc.) · `BIE` = Bien (produit physique : marchandise, fourniture, équipement, etc.). |
| 11 | **L** | `unitPriceMode` | Texte | ✅ Oui | **Mode du prix unitaire.** Précise si le prix en colonne H est hors taxes ou toutes taxes comprises. Valeurs : `ht` = Le prix ne comprend pas les taxes (la DGI calculera la taxe en sus) · `ttc` = Le prix inclut déjà les taxes (la DGI extraira la part de taxe). |
| 13 | **N** | `unit` | Texte | ❌ Non | **Unité de mesure.** Unité dans laquelle la quantité est exprimée. Champ informatif non transmis à la DGI dans la version actuelle. Exemples : `pièce`, `kg`, `heure`, `jour`, `m²`. |

---

### 3.4 Informations fiscales et taxe spécifique

| Col. | Lettre | Nom de l'en-tête | Type | Obligatoire | Description détaillée |
|:----:|:------:|-------------------|------|:-----------:|----------------------|
| 14 | **O** | `specificTaxAmount` | Nombre décimal | ❌ Non | **Montant de la taxe spécifique.** Utilisé pour les articles soumis à une taxe fixe par unité (et non un pourcentage). Par exemple, une taxe de 500 CDF par litre de carburant. Si renseigné, ce montant est ajouté au calcul du total de la facture en plus de la taxe de groupe (colonne J). Si supérieur à 0, le champ `taxSpecificValue` (colonne P) devient recommandé. |
| 15 | **P** | `taxSpecificValue` | Texte | ❌ Non | **Valeur descriptive de la taxe spécifique.** Complément d'information lié à la taxe spécifique (colonne O). Peut contenir le libellé ou le code réglementaire de la taxe. Recommandé lorsque `specificTaxAmount` est renseigné, afin que la DGI puisse identifier la nature de la taxe spécifique appliquée. Exemple : `TAXE_CARBURANT`, `DROIT_ACCISE`. |

---

### 3.5 Champs pour factures d'avoir (Credit Note)

> Ces colonnes sont **obligatoires uniquement si `type` = `AV`** (colonne B). Elles permettent de lier l'avoir à la facture originale, conformément aux exigences de la DGI.

| Col. | Lettre | Nom de l'en-tête | Type | Obligatoire | Description détaillée |
|:----:|:------:|-------------------|------|:-----------:|----------------------|
| 17 | **R** | `reference` | Texte (24 car. max) | ⚠️ Requis si AV | **Numéro de la facture originale annulée.** Doit contenir le `rn` exact de la facture de vente (`FV`) que cet avoir annule ou corrige. La DGI vérifiera que cette facture originale existe et a bien été confirmée. Exemple : si l'avoir annule `FV-2026-00001`, cette cellule doit contenir `FV-2026-00001`. |
| 18 | **S** | `referenceType` | Texte | ⚠️ Requis si AV | **Type de référence / motif de l'avoir.** Indique la raison pour laquelle l'avoir est émis. Cela aide la DGI à catégoriser l'annulation. Exemples de valeurs : `CANCEL` = Annulation complète · `PARTIAL` = Annulation partielle · `CORRECTION` = Correction d'erreur. |
| 19 | **T** | `referenceDesc` | Texte | ❌ Non | **Description libre du motif de l'avoir.** Texte explicatif de la raison de l'annulation ou de la correction. Champ libre pour la traçabilité. Exemple : `Annulation suite à résiliation du contrat`, `Correction d'erreur de quantité`. |

---

### 3.6 Champs pour devises étrangères

> Ces colonnes sont nécessaires **uniquement si la facture est libellée dans une devise différente** de la devise locale. Si `curCode` est renseigné, `curDate` et `curRate` deviennent obligatoires.

| Col. | Lettre | Nom de l'en-tête | Type | Obligatoire | Description détaillée |
|:----:|:------:|-------------------|------|:-----------:|----------------------|
| 20 | **U** | `curCode` | Texte (3 car.) | ❌ Non | **Code de la devise étrangère.** Code ISO 4217 de la devise étrangère utilisée. Renseigner ce champ déclenche la conversion de devise. Le montant total sera calculé dans la devise locale en appliquant le taux de change. Exemples : `USD`, `EUR`, `GBP`. |
| 21 | **V** | `curDate` | Date ou Texte | ⚠️ Si curCode | **Date du taux de change.** Date à laquelle le taux de change a été fixé. Sert de référence pour la conformité fiscale. Formats acceptés : `yyyy-MM-dd` (ex : `2026-02-23`), `yyyy-MM-ddTHH:mm:ss`, ou format date Excel natif. |
| 22 | **W** | `curRate` | Nombre décimal | ⚠️ Si curCode | **Taux de change appliqué.** Facteur de conversion de la devise étrangère vers la devise locale. Doit être **strictement supérieur à 0**. Exemple : si 1 USD = 2750 CDF, renseigner `2750`. Le Middleware calculera : `total_local = total_devise × curRate`. |

---

## 4. Colonnes de sortie — Ce que FACTS reçoit en retour

Après traitement, le Middleware retourne le **même fichier Excel enrichi** : les colonnes d'entrée (A à W) restent inchangées, et les colonnes X à AD sont **remplies avec les résultats** de la soumission DGI pour chaque ligne.

---

### 4.1 Champs de succès (remplis par la DGI)

Ces champs sont remplis **lorsque la facture a été acceptée et confirmée** par la DGI (Phase 1 + Phase 2 réussies). Si la facture est rejetée, ces colonnes restent vides et les colonnes d'erreur (X, Y) sont remplies.

| Col. | Lettre | Nom | Source | Description détaillée |
|:----:|:------:|------|--------|----------------------|
| 25 | **Z** | `dateTime` | DGI | **Horodatage officiel de la validation.** Date et heure exactes auxquelles la DGI a validé et enregistré la facture électronique. C'est le **timestamp officiel** qui fait foi fiscalement. Format : `yyyy-MM-ddTHH:mm:ss`. FACTS doit stocker cette valeur comme date officielle de la facture électronique. |
| 26 | **AA** | `qrCode` | DGI | **Code QR de la facture électronique.** Chaîne de données générée par la DGI qui encode les informations clés de la facture validée. Ce QR code **doit être imprimé sur la facture physique** remise au client, conformément à la réglementation DGI. FACTS doit le convertir en image QR pour l'impression. |
| 27 | **AB** | `codeDEFDGI` | DGI | **Code DEF (Document Électronique Fiscal) officiel.** Numéro unique attribué par la DGI à cette facture dans le système eDEF. C'est le **numéro officiel de la facture électronique** (E-Invoice number). FACTS doit le conserver comme preuve de conformité fiscale. |
| 28 | **AC** | `counters` | DGI | **Compteurs de la machine émettrice.** Chaîne retournée par la DGI contenant les compteurs séquentiels de facturation. Utilisé pour la traçabilité et l'audit. Format variable selon la DGI. FACTS doit le stocker tel quel. |
| 29 | **AD** | `nim` | DGI | **Numéro d'Identification Machine.** Identifiant unique de la machine/caisse émettrice enregistrée auprès de la DGI. Permet à la DGI de tracer l'origine de l'émission. FACTS doit le stocker pour la traçabilité. |

---

### 4.2 Champs d'erreur

Ces champs sont remplis **lorsque la facture a été rejetée** par la DGI ou qu'une erreur s'est produite. Si la facture est acceptée, ces colonnes restent vides.

| Col. | Lettre | Nom | Source | Description détaillée |
|:----:|:------:|------|--------|----------------------|
| 23 | **X** | `errorCode` | Bridge / DGI | **Code d'erreur technique.** Identifiant court et standardisé de l'erreur. Peut provenir du Middleware (validation locale) ou de la DGI (rejet). Exemples Bridge : `DGI_PHASE1_ERROR`, `DGI_PHASE2_ERROR`. Les codes DGI natifs sont transmis tels quels. Si cette cellule est vide, la facture a été traitée avec succès. |
| 24 | **Y** | `errorDesc` | Bridge / DGI | **Description de l'erreur en texte clair.** Explication détaillée et lisible de la raison du rejet ou de l'échec. Exemples : `Le NIF du client est invalide`, `Facture déjà soumise avec ce RN`, `Erreur de connexion à la DGI`. FACTS peut afficher ce message à l'utilisateur pour correction. |

---

## 5. Tableau récapitulatif complet des colonnes

### Colonnes d'ENTRÉE (remplies par FACTS) — A à W

| Col. | Lettre | Champ | Type | Obligatoire | Résumé |
|:----:|:------:|-------|------|:-----------:|--------|
| 0 | A | `rn` | Texte | ✅ | N° facture unique |
| 1 | B | `type` | Texte | ✅ | `FV` (Facture) ou `AV` (Avoir) |
| 2 | C | `clientNif` | Texte | ⚠️ sauf PP | NIF client (commence par `NIF`) |
| 3 | D | `clientName` | Texte | ✅ | Nom / raison sociale client |
| 4 | E | `clientType` | Texte | ✅ | `PP` · `PM` · `PC` · `PL` · `AO` |
| 5 | F | `itemCode` | Texte | ✅ | Code article |
| 6 | G | `itemName` | Texte | ✅ | Désignation article |
| 7 | H | `itemPrice` | Nombre | ✅ | Prix unitaire (> 0) |
| 8 | I | `itemQuantity` | Nombre | ✅ | Quantité (> 0) |
| 9 | J | `itemTaxGroup` | Texte | ✅ | Groupe de taxe (`A`, `B`, `C`…) |
| 10 | K | `itemArticleType` | Texte | ✅ | `SER` (Service) ou `BIE` (Bien) |
| 11 | L | `unitPriceMode` | Texte | ✅ | `ht` ou `ttc` |
| 12 | M | `currency` | Texte | ✅ | Devise ISO (`CDF`, `USD`…) |
| 13 | N | `unit` | Texte | ❌ | Unité de mesure |
| 14 | O | `specificTaxAmount` | Nombre | ❌ | Montant taxe spécifique |
| 15 | P | `taxSpecificValue` | Texte | ❌ | Valeur/code taxe spécifique |
| 16 | Q | `mode` | Texte/Nbr | ❌ | `ht`/`0` ou `ttc`/`1` |
| 17 | R | `reference` | Texte | ⚠️ si AV | RN facture originale |
| 18 | S | `referenceType` | Texte | ⚠️ si AV | Type de référence |
| 19 | T | `referenceDesc` | Texte | ❌ | Motif de l'avoir |
| 20 | U | `curCode` | Texte | ❌ | Code devise étrangère |
| 21 | V | `curDate` | Date | ⚠️ si U | Date du taux de change |
| 22 | W | `curRate` | Nombre | ⚠️ si U | Taux de change (> 0) |

### Colonnes de SORTIE (remplies par le Middleware) — X à AD

| Col. | Lettre | Champ | Source | Résumé |
|:----:|:------:|-------|--------|--------|
| 23 | X | `errorCode` | Bridge / DGI | Code d'erreur (vide si succès) |
| 24 | Y | `errorDesc` | Bridge / DGI | Description de l'erreur |
| 25 | Z | `dateTime` | DGI | Horodatage officiel de validation |
| 26 | AA | `qrCode` | DGI | QR Code à imprimer sur la facture |
| 27 | AB | `codeDEFDGI` | DGI | N° officiel E-Invoice (code DEF) |
| 28 | AC | `counters` | DGI | Compteurs de la machine émettrice |
| 29 | AD | `nim` | DGI | N° d'Identification Machine |

**Légende :**
- ✅ = Obligatoire pour toute facture
- ⚠️ = Obligatoire sous condition (voir description)
- ❌ = Optionnel

---

## 6. Règles de validation appliquées par le Middleware

Avant de soumettre à la DGI, le Middleware vérifie chaque ligne. En cas d'erreur, la ligne est rejetée et le message apparaît dans la réponse ou dans la colonne Y du fichier retourné.

| # | Règle | Colonnes | Message d'erreur |
|---|-------|----------|-----------------|
| 1 | Le RN est obligatoire et non vide | A | `RN manquant` |
| 2 | Le type de facture est obligatoire | B | `Type de facture manquant` |
| 3 | Le NIF client est obligatoire (sauf PP) | C, E | `NIF client manquant` ou `NIF client obligatoire pour le type PM` |
| 4 | Le NIF doit commencer par `NIF` | C | `Format NIF invalide. Doit commencer par 'NIF'` |
| 5 | Le nom client est obligatoire | D | `Nom client manquant` |
| 6 | Le nom client est obligatoire pour PP | D, E | `Nom client obligatoire pour les clients PP` |
| 7 | Le type client doit être valide | E | `Type de client invalide. Doit être: PP, PM, PC, PL ou AO` |
| 8 | Le code article est obligatoire | F | `Code article manquant` |
| 9 | Le nom article est obligatoire | G | `Nom article manquant` |
| 10 | Le prix est obligatoire et > 0 | H | `Prix manquant` ou `Le prix doit être supérieur à 0` |
| 11 | La quantité est obligatoire et > 0 | I | `Quantité manquante` ou `La quantité doit être supérieure à 0` |
| 12 | Le groupe de taxe est obligatoire | J | `Groupe de taxe manquant` |
| 13 | Le type d'article est obligatoire | K | `Type d'article manquant` |
| 14 | Le mode de prix est obligatoire | L | `Mode de prix manquant` |
| 15 | La devise est obligatoire | M | `Devise manquante` |
| 16 | Si curCode fourni → curDate obligatoire | U, V | `Date de devise manquante quand le code devise est fourni` |
| 17 | Si curCode fourni → curRate obligatoire et > 0 | U, W | `Taux de change manquant quand le code devise est fourni` ou `Le taux de change doit être supérieur à 0` |
| 18 | curRate doit être un nombre valide | W | `Format de taux de change invalide` |

---

## 7. Exemples de fichiers Excel

### 7.1 Facture standard

Une facture de vente simple en Franc Congolais :

| A | B | C | D | E | F | G | H | I | J | K | L | M |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `FV-2026-00001` | `FV` | `NIF1234567890` | `Société ABC SARL` | `PM` | `SERV-001` | `Consulting informatique` | `150000` | `2` | `A` | `SER` | `ht` | `CDF` |

> Colonnes N à W : vides (pas de taxe spécifique, pas d'avoir, pas de devise étrangère).

---

### 7.2 Facture d'avoir (Credit Note)

Un avoir annulant la facture `FV-2026-00001` :

| A | B | C | D | E | F | G | H | I | J | K | L | M | … | R | S | T |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `AV-2026-00001` | `AV` | `NIF1234567890` | `Société ABC SARL` | `PM` | `SERV-001` | `Consulting informatique` | `150000` | `2` | `A` | `SER` | `ht` | `CDF` | … | `FV-2026-00001` | `CANCEL` | `Annulation contrat` |

> ⚠️ Les colonnes R, S sont **obligatoires** pour un avoir. La colonne R doit contenir le RN exact de la facture originale.

---

### 7.3 Facture en devise étrangère

Une facture en USD avec conversion en CDF :

| A | B | C | D | E | F | G | H | I | J | K | L | M | … | U | V | W |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `FV-2026-00002` | `FV` | `NIF9876543210` | `Client International` | `PM` | `ART-005` | `Équipement réseau` | `500` | `3` | `B` | `BIE` | `ht` | `USD` | … | `USD` | `2026-02-23` | `2750` |

> Le Middleware calculera : total_USD = 500 × 3 = 1500 USD, total_CDF = 1500 × 2750 = 4 125 000 CDF.

---

### 7.4 Fichier Excel retourné après traitement

Après traitement, FACTS reçoit le même fichier avec les colonnes X à AD remplies :

**Ligne acceptée (FV-2026-00001) :**

| … colonnes A-W inchangées … | X (errorCode) | Y (errorDesc) | Z (dateTime) | AA (qrCode) | AB (codeDEFDGI) | AC (counters) | AD (nim) |
|---|---|---|---|---|---|---|---|
| … | *(vide)* | *(vide)* | `2026-02-23T14:30:15` | `QR_DATA_ABC123...` | `DEF-2026-456789` | `FV:001234` | `NIM-XYZ-001` |

**Ligne rejetée (FV-2026-00003) :**

| … colonnes A-W inchangées … | X (errorCode) | Y (errorDesc) | Z (dateTime) | AA (qrCode) | AB (codeDEFDGI) | AC (counters) | AD (nim) |
|---|---|---|---|---|---|---|---|
| … | `INVALID_NIF` | `Le NIF du client est invalide ou non reconnu` | *(vide)* | *(vide)* | *(vide)* | *(vide)* | *(vide)* |

**Comment FACTS doit interpréter le fichier retourné :**

| Condition | Signification | Action FACTS |
|-----------|---------------|--------------|
| Colonne X vide **ET** colonne AB remplie | ✅ Facture **acceptée** par la DGI | Stocker `codeDEFDGI`, `qrCode`, `dateTime`, `counters`, `nim`. Marquer la facture comme validée. |
| Colonne X remplie | ❌ Facture **rejetée** | Lire `errorCode` (X) et `errorDesc` (Y) pour comprendre l'erreur. Corriger et resoumettre. |
| Colonne X vide **ET** colonne AB vide | ⏳ Facture en **attente** (Phase 1 OK, Phase 2 échouée) | Resoumettre le fichier ou cette facture via l'API JSON. |

---

## 8. Réponses HTTP possibles

| Code HTTP | Condition | Body |
|-----------|-----------|------|
| **200 OK** | Toutes les lignes traitées avec succès | Fichier `.xlsx` enrichi (Content-Type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`) |
| **206 Partial Content** | Certaines lignes en erreur | JSON avec résumé (voir ci-dessous) |
| **400 Bad Request** | Fichier illisible ou format invalide | JSON avec message d'erreur |
| **401 Unauthorized** | Token JWT manquant ou invalide | JSON : `"Entreprise non trouvée"` |
| **500 Internal Server Error** | Erreur interne du Middleware | JSON avec message d'erreur |

**Réponse 206 (succès partiel) :**
```json
{
  "message": "Import terminé. 8 factures créées avec succès. 2 erreurs.",
  "errors": [
    "Ligne 5: NIF client manquant",
    "Ligne 9: Le prix doit être supérieur à 0"
  ],
  "createdInvoiceNumbers": ["FV-001", "FV-002", "FV-003", "..."],
  "successCount": 8,
  "errorCount": 2
}
```

> **Note** : Les numéros de ligne correspondent aux lignes du fichier Excel (ligne 2 = première facture après l'en-tête).

---

## 9. FAQ Excel

### Q1 : Puis-je mélanger factures et avoirs dans le même fichier ?

**Oui.** Chaque ligne est traitée indépendamment. Le type (`FV` ou `AV`) est lu dans la colonne B. Assurez-vous que les avoirs (`AV`) ont bien les colonnes R et S remplies avec la référence de la facture originale.

---

### Q2 : Que se passe-t-il si une ligne a une erreur ?

Les autres lignes sont quand même traitées. Seule la ligne en erreur est ignorée. L'erreur est signalée dans la réponse JSON (code 206) et/ou dans les colonnes X et Y du fichier retourné.

---

### Q3 : Puis-je resoumettre un fichier avec des lignes déjà confirmées ?

Les lignes dont le `rn` a déjà été confirmé seront rejetées avec le code `INVOICE_ALREADY_CONFIRMED`. Les autres lignes seront traitées normalement. Cela n'affecte pas les factures déjà validées.

---

### Q4 : Une facture peut-elle avoir plusieurs articles dans l'Excel ?

Dans la version actuelle, **chaque ligne = 1 facture avec 1 article**. Pour les factures multi-articles, utilisez l'API JSON (`POST /api/invoice`) qui accepte un tableau `items[]`. L'import Excel avec plusieurs lignes ayant le même `rn` créera des factures distinctes.

---

### Q5 : Le fichier retourné conserve-t-il le formatage de l'original ?

**Oui.** Le Middleware lit et réécrit le même fichier `.xlsx`. Le formatage, les styles et les données originales sont préservés. Seules les colonnes de sortie (X à AD) sont ajoutées/modifiées.

---

### Q6 : Y a-t-il une limite de lignes ?

Pas de limite technique stricte, mais pour des performances optimales nous recommandons **≤ 200 lignes par fichier**. Au-delà, le temps de traitement peut dépasser le timeout. Pour des volumes importants, découpez en plusieurs fichiers.

---

### Q7 : Comment corriger une facture déjà envoyée ?

La DGI **ne permet pas** la modification d'une facture confirmée. Il faut :
1. Envoyer un **avoir** (`type = AV`) référençant la facture originale dans la colonne R.
2. Une fois l'avoir confirmé, envoyer une **nouvelle facture corrigée** avec un nouveau `rn`.

---

### Q8 : Les colonnes doivent-elles avoir un en-tête précis ?

L'en-tête (ligne 1) est **ignoré** par le Middleware. Vous pouvez nommer les colonnes comme vous le souhaitez. Seul l'**ordre des colonnes** (A = rn, B = type, C = clientNif, etc.) compte.

---

### Q9 : Quel encodage et format de fichier ?

Le fichier doit être au format **Microsoft Excel `.xlsx`** (OOXML). Les formats `.xls` (ancien format), `.csv`, `.ods` ne sont **pas** supportés.

---

*Document généré pour l'équipe d'intégration FACTS — volet Import Excel. Pour toute question, contacter l'équipe Middleware.*
