# 📋 Guide de Soumission de Facture DGI - Deux Étapes

## 🎯 Vue d'ensemble

Le processus de soumission de factures à la DGI (Direction Générale des Impôts) est divisé en **deux étapes distinctes** :

### **PHASE 1: Soumission (Submission)**
- Soumet la facture brute à la DGI
- Retourne un **UID unique** et les totaux calculés
- Status: `PHASE1`
- **Réponse utilisateur**: ✓ Facture soumise. En attente de confirmation.

### **PHASE 2: Confirmation (Verification)**
- Confirme la soumission avec les totaux validés
- Retourne le **Code QR**, **Code DEF**, et autres données officielles
- Status: `CONFIRMED`
- **Réponse utilisateur**: ✓ Facture validée et confirmée par la DGI.

---

## 📊 Architecture du Service

### Classes Principales

#### 1. **DgiService** (Service Principal)
```java
// Soumet une facture à la PHASE 1
DgiResponse submitInvoicePhase1(InvoiceEntity invoice, String dgiToken)

// Confirme une facture à la PHASE 2
DgiResponse confirmInvoicePhase2(InvoiceEntity invoice, String dgiToken)

// Complète automatiquement les deux phases (rétro-compatibilité)
DgiResponse submitInvoice(InvoiceEntity invoice, String dgiToken)
```

#### 2. **DgiResponse** (Réponse du Service)
Contient:
- ✅ Status et succès
- 📦 Données PHASE 1 (uid, total, curTotal, vtotal)
- 📤 Données PHASE 2 (qrCode, dateTime, codeDEFDGI, nim)
- ❌ Informations d'erreur

#### 3. **InvoiceSubmissionResponse** (Réponse Utilisateur)
Structure complète avec deux phases imbriquées:
```json
{
  "invoiceNumber": "FAC001",
  "status": "CONFIRMED",
  "isComplete": true,
  "phase1": {
    "uid": "DGI-UUID-12345",
    "total": 1000.00,
    "message": "Facture soumise avec succès"
  },
  "phase2": {
    "qrCode": "00000000000000000000...",
    "dateTime": "2025-01-18T14:30:00",
    "codeDEFDGI": "DEF-2025-001",
    "message": "Facture validée et confirmée"
  }
}
```

#### 4. **InvoiceResponseMapper** (Transformation)
Convertit les réponses DGI en réponses utilisateur claires

---

## 🔄 Flux d'Exécution

### Scénario 1: Soumission Simple (Les deux phases)
```
1. POST /api/invoice avec facture
2. Validation de base
3. PHASE 1: submitInvoicePhase1() → Reçoit UID
4. PHASE 2: confirmInvoicePhase2() → Reçoit QR Code
5. Return: InvoiceSubmissionResponse avec status = "CONFIRMED"
```

### Scénario 2: Soumission en Attente de Confirmation
```
1. POST /api/invoice avec facture
2. PHASE 1: submitInvoicePhase1() → Succès, reçoit UID
3. ❌ PHASE 2: confirmInvoicePhase2() → Erreur
4. Return: InvoiceSubmissionResponse avec status = "PHASE1" + error
5. Utilisateur peut réessayer Phase 2 ultérieurement
```

### Scénario 3: Erreur en Phase 1
```
1. POST /api/invoice avec facture
2. ❌ PHASE 1: submitInvoicePhase1() → Erreur
3. Return: InvoiceSubmissionResponse avec status = "FAILED" + error
4. La facture n'est pas sauvegardée
```

---

## 📝 Intégration dans le Resource

### Avant (Ancien Code)
```java
DgiResponse dgiResponse = dgiService.submitInvoice(invoice, token);
if (dgiResponse.success) {
    // OK
} else {
    // Erreur
}
```

### Après (Nouveau Code - Recommandé)
```java
// Option 1: Soumission complète
DgiResponse response = dgiService.submitInvoice(invoice, token);
InvoiceSubmissionResponse userResponse = 
    InvoiceResponseMapper.mapCompleteResponse(invoice, response);

// Option 2: Contrôle par phases
DgiResponse phase1 = dgiService.submitInvoicePhase1(invoice, token);
if (phase1.success) {
    DgiResponse phase2 = dgiService.confirmInvoicePhase2(invoice, token);
    InvoiceSubmissionResponse userResponse = 
        InvoiceResponseMapper.mapPhase2Response(invoice, phase2);
}
```

---

## 🛡️ Gestion des Erreurs

### Codes d'Erreur

| Code | Phase | Signification | Action |
|------|-------|---------------|--------|
| `DGI_PHASE1_ERROR` | 1 | Erreur soumission | Vérifier données, réessayer |
| `DGI_PHASE2_ERROR` | 2 | Erreur confirmation | Réessayer avec Phase 2 |
| `INVOICE_ALREADY_SUBMITTED` | 1 | Facture déjà soumise | Vérifier UID existant |
| `INVALID_INVOICE_STATUS` | 2 | Statut incorrect | Terminer Phase 1 d'abord |
| `MISSING_UID` | 2 | UID manquant | Completer Phase 1 d'abord |

### Exemples de Réponse d'Erreur

**Erreur Phase 1:**
```json
{
  "status": "FAILED",
  "isComplete": false,
  "error": {
    "code": "DGI_PHASE1_ERROR",
    "description": "Numéro de facture déjà utilisé",
    "userMessage": "Cette facture existe déjà. Veuillez utiliser un numéro unique.",
    "failedPhase": "PHASE1"
  }
}
```

**Erreur Phase 2:**
```json
{
  "status": "PHASE1",
  "isComplete": false,
  "phase1": { ... },
  "error": {
    "code": "DGI_PHASE2_ERROR",
    "description": "Timeout lors de la confirmation",
    "userMessage": "La confirmation a échoué. Veuillez réessayer.",
    "failedPhase": "PHASE2"
  }
}
```

---

## 📱 Utilisation Côté Frontend

### Cas 1: Utilisateur Soumet une Facture
```javascript
POST /api/invoice
Body: { rn: "FAC001", montant: 1000, ... }

Response (200 OK):
{
  "status": "CONFIRMED",
  "phase1": { "uid": "..." },
  "phase2": { "qrCode": "..." }
}

Afficher: "✓ Facture validée avec succès!"
Télécharger QR Code
```

### Cas 2: Erreur Phase 1
```javascript
Response (200 OK):
{
  "status": "FAILED",
  "error": { 
    "code": "INVALID_DATA",
    "userMessage": "Montant invalide..."
  }
}

Afficher: "✗ Erreur: Montant invalide..."
Proposer correction
```

### Cas 3: Erreur Phase 2 (mais Phase 1 OK)
```javascript
Response (200 OK):
{
  "status": "PHASE1",
  "phase1": { "uid": "DGI-123" },
  "error": { 
    "failedPhase": "PHASE2",
    "userMessage": "La confirmation a échoué..."
  }
}

Afficher: "⏳ Facture soumise mais confirmation en attente"
"Bouton: Confirmer maintenant (avec UID)"
```

---

## 🔍 Logging et Débogage

### Logs Générés (avec Java Logger)

```
INFO: === PHASE 1: Soumission de la facture RN=FAC001 ===
INFO: [PHASE 1 Response] HTTP 200
FINE: Response body: {"uid":"DGI-12345",...}

INFO: === PHASE 2: Confirmation de la facture UID=DGI-12345 ===
INFO: [PHASE 2 Response] HTTP 200
FINE: Response body: {"qrCode":"...",...}

INFO: ✓ PHASE 1 complétée avec succès - UID: DGI-12345
INFO: ✓ PHASE 2 complétée avec succès - QR Code: ...
```

---

## ✅ Avantages de Cette Architecture

1. **Clarté**: Deux phases distinctes et bien documentées
2. **Flexibilité**: Possibilité d'effectuer Phase 1 et Phase 2 séparément
3. **Résilience**: Gestion granulaire des erreurs par phase
4. **UX Améliorée**: Messages clairs pour l'utilisateur
5. **Rétro-compatibilité**: L'ancienne méthode `submitInvoice()` fonctionne toujours
6. **Testabilité**: Chaque phase peut être testée indépendamment

---

## 📋 Checklist d'Implémentation

- ✅ Refactoriser `DgiService` avec `submitInvoicePhase1()` et `confirmInvoicePhase2()`
- ✅ Améliorer `DgiResponse` avec documentation
- ✅ Créer `InvoiceSubmissionResponse` pour les utilisateurs
- ✅ Créer `InvoiceResponseMapper` pour la transformation
- ✅ Mettre à jour `InvoiceResource` pour utiliser le mapper
- ⬜ Ajouter des endpoints séparés pour Phase 1 et Phase 2 (futur)
- ⬜ Ajouter des tests unitaires pour chaque phase
- ⬜ Documenter les APIs dans Swagger
