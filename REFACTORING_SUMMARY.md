# ✅ RÉSUMÉ DES AMÉLIORATIONS - DgiService v2

## 🎯 Changement Principal

**Avant**: Service retourne `DgiResponse` → Resource doit persister l'entité  
**Après**: Service retourne `InvoiceEntity` mise à jour et sauvegardée ✅

---

## 📊 Architecture Nouvelle

```
┌─────────────────────────────────────────────────────────┐
│                   InvoiceResource                       │
│  (POST /api/invoice avec InvoiceEntity)                 │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
         ┌─────────────────────────────┐
         │   Validation de base        │
         │   - RN valide?              │
         │   - Email valide?           │
         │   - Entreprise existe?      │
         └──────────┬──────────────────┘
                    │
                    ▼
         ┌──────────────────────────────┐
         │  Entité existante?           │
         ├──────────────────────────────┤
         │  OUI → Récupérer            │
         │  NON → Créer + Persister    │
         └──────────┬───────────────────┘
                    │
                    ▼
      ┌─────────────────────────────────────┐
      │   DgiService.submitInvoice()        │
      │   Retourne InvoiceEntity mise à jour │
      └──────────────┬──────────────────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
        ▼                         ▼
   ┌─────────────┐         ┌──────────────┐
   │  PHASE 1    │         │   PHASE 2    │
   │  Soumission │         │  Confirmation│
   │   (POST)    │         │    (PUT)     │
   └──────┬──────┘         └──────┬───────┘
          │                       │
          ▼                       ▼
   Reçoit:              Reçoit:
   - uid ✓              - qrCode ✓
   - total              - dateTime
   - vtotal             - codeDEFDGI
                        - nim
   │                    │
   └────────┬───────────┘
            │
            ▼
    ┌──────────────────┐
    │  Entité mise à   │
    │  jour + Persist  │
    └────────┬─────────┘
             │
             ▼
    ┌──────────────────────┐
    │ InvoiceEntityResponse │
    │      Mapper          │
    └────────┬─────────────┘
             │
             ▼
    ┌──────────────────────┐
    │  JSON au client      │
    │  - Status            │
    │  - Message clair     │
    │  - Données Phase 1/2 │
    │  - Erreurs (si any)  │
    └──────────────────────┘
```

---

## 📋 Statuts et Transitions

```
PENDING (initial)
  │
  ├─ ✗ Phase 1 échoue → PENDING + errorCode/errorDesc
  │
  └─ ✓ Phase 1 réussit
     │
     └─> PHASE1 (en attente de Phase 2)
        │
        ├─ ✗ Phase 2 échoue → PHASE1 + errorCode/errorDesc
        │                     (peut réessayer Phase 2)
        │
        └─ ✓ Phase 2 réussit
           │
           └─> CONFIRMED + toutes les données DGI ✓
```

---

## 🔄 Flux d'Exécution Détaillé

### Phase 1: submitInvoicePhase1()

```java
public InvoiceEntity submitInvoicePhase1(InvoiceEntity invoice, String dgiToken) {
    1. LOG: "=== PHASE 1: Soumission RN=xxx ==="
    
    2. Vérifications:
       - Déjà PHASE1? → errorCode + persist + return
       - Déjà CONFIRMED? → errorCode + persist + return
    
    3. Appel API:
       POST https://developper.dgirdc.cd/edef/api/invoice
       Headers: Authorization, Content-Type, Accept
       Body: Invoice complète en JSON
    
    4. Réponse:
       - Erreur? → invoice.errorCode + invoice.errorDesc + persist + return
       - Succès? → Extraire uid, total, curTotal, vtotal
    
    5. Mise à jour:
       invoice.uid = uid
       invoice.total = total
       invoice.curTotal = curTotal
       invoice.vtotal = vtotal
       invoice.status = "PHASE1"
       invoice.errorCode = null
       invoice.errorDesc = null
       invoice.persist() ✓
    
    6. LOG: "✓ PHASE 1 complétée - UID: xxx"
    
    7. Return invoice mise à jour
}
```

### Phase 2: confirmInvoicePhase2()

```java
public InvoiceEntity confirmInvoicePhase2(InvoiceEntity invoice, String dgiToken) {
    1. LOG: "=== PHASE 2: Confirmation UID=xxx ==="
    
    2. Vérifications:
       - Status != PHASE1? → errorCode + persist + return
       - UID vide? → errorCode + persist + return
    
    3. Appel API:
       PUT https://developper.dgirdc.cd/edef/api/invoice/{uid}/confirm
       Body: { total, vtotal }
    
    4. Réponse:
       - Erreur? → invoice.errorCode + invoice.errorDesc + 
                    status = "PHASE1" (reste en Phase 1) + persist + return
       - Succès? → Extraire qrCode, dateTime, codeDEFDGI, nim
    
    5. Mise à jour:
       invoice.qrCode = qrCode
       invoice.dateTime = dateTime
       invoice.codeDEFDGI = codeDEFDGI
       invoice.counters = counters
       invoice.nim = nim
       invoice.status = "CONFIRMED"
       invoice.errorCode = null
       invoice.errorDesc = null
       invoice.persist() ✓
    
    6. LOG: "✓ PHASE 2 complétée - QR: xxx"
    
    7. Return invoice mise à jour
}
```

### submitInvoice() (Les deux phases)

```java
public InvoiceEntity submitInvoice(InvoiceEntity invoice, String dgiToken) {
    1. Phase 1:
       invoice = submitInvoicePhase1(invoice, dgiToken)
    
    2. Vérifier succès Phase 1:
       if (!"PHASE1".equals(invoice.status)) {
           return invoice  // Erreur Phase 1 → arrêt
       }
    
    3. Phase 2:
       return confirmInvoicePhase2(invoice, dgiToken)
}
```

---

## 📦 Champs de InvoiceEntity Utilisés

### À la soumission (Phase 1):
```
Input:  rn, email, nif, isf, items[], client, operator, dates, amounts, currency
Output: uid, total, curTotal, vtotal, status = "PHASE1"
```

### À la confirmation (Phase 2):
```
Input:  uid, total, vtotal (depuis Phase 1)
Output: qrCode, dateTime, codeDEFDGI, counters, nim, status = "CONFIRMED"
```

### En cas d'erreur:
```
Output: errorCode, errorDesc, status = "PENDING" ou "PHASE1"
```

---

## 📤 Exemples de Réponses InvoiceEntity

### ✅ CONFIRMED (Succès complet)
```json
{
  "id": "uuid-123",
  "rn": "FAC001",
  "email": "user@example.com",
  "nif": "NIF123",
  "status": "CONFIRMED",
  "uid": "DGI-12345678",
  "total": 1000.00,
  "vtotal": 200.00,
  "curTotal": 1000.00,
  "qrCode": "00000000000000000000000000...",
  "dateTime": "2025-01-18T14:30:00",
  "codeDEFDGI": "DEF-2025-001",
  "nim": "NIM123",
  "errorCode": null,
  "errorDesc": null,
  "createdAt": "2025-01-18T10:00:00",
  "updatedAt": "2025-01-18T14:30:00"
}
```

### ⏳ PHASE1 (En attente Phase 2)
```json
{
  "id": "uuid-123",
  "rn": "FAC002",
  "status": "PHASE1",
  "uid": "DGI-87654321",
  "total": 500.00,
  "vtotal": 100.00,
  "curTotal": 500.00,
  "qrCode": null,
  "dateTime": null,
  "codeDEFDGI": null,
  "nim": null,
  "errorCode": null,
  "errorDesc": null,
  "updatedAt": "2025-01-18T11:00:00"
}
```

### ❌ PENDING (Erreur Phase 1)
```json
{
  "id": "uuid-123",
  "rn": "FAC003",
  "status": "PENDING",
  "uid": null,
  "total": null,
  "errorCode": "INVALID_DATA",
  "errorDesc": "Le numéro de facture est invalide",
  "updatedAt": "2025-01-18T11:05:00"
}
```

### ❌ PHASE1 + Erreur (Erreur Phase 2)
```json
{
  "id": "uuid-123",
  "rn": "FAC004",
  "status": "PHASE1",
  "uid": "DGI-11111111",
  "total": 750.00,
  "vtotal": 150.00,
  "qrCode": null,
  "dateTime": null,
  "errorCode": "DGI_PHASE2_ERROR",
  "errorDesc": "Timeout lors de la confirmation. Veuillez réessayer.",
  "updatedAt": "2025-01-18T11:10:00"
}
```

---

## 🗄️ Schéma BD (InvoiceEntity)

```sql
CREATE TABLE invoicerntity (
  id UUID PRIMARY KEY,
  
  -- Identification
  email VARCHAR(200),
  uid VARCHAR(200),      -- ← Reçu Phase 1
  nif VARCHAR(200),
  rn VARCHAR(255),       -- Numéro de facture
  
  -- Montants
  total DECIMAL(15,2),   -- ← Reçu Phase 1
  vtotal DECIMAL(15,2),  -- ← Reçu Phase 1
  curTotal DECIMAL(15,2),-- ← Reçu Phase 1
  
  -- Confirmation DGI
  qrCode TEXT,           -- ← Reçu Phase 2
  dateTime VARCHAR(50),  -- ← Reçu Phase 2
  codeDEFDGI VARCHAR(50),-- ← Reçu Phase 2
  counters VARCHAR(50),  -- ← Reçu Phase 2
  nim VARCHAR(50),       -- ← Reçu Phase 2
  
  -- Statuts
  status VARCHAR(20),    -- PENDING, PHASE1, CONFIRMED
  error_code VARCHAR(100),
  error_desc TEXT,
  
  -- Métadonnées
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  
  ...
);
```

---

## 🧪 Scénarios de Test

### Scénario 1: ✅ Succès complet
```
1. POST /api/invoice avec nouvelle RN
2. Phase 1: ✓ Reçoit uid, total, vtotal
3. DB: status = "PHASE1", uid = "DGI-xxx"
4. Phase 2: ✓ Reçoit qrCode, dateTime, codeDEFDGI, nim
5. DB: status = "CONFIRMED", qrCode = "..."
6. Client: Message "Facture validée et confirmée" ✓
```

### Scénario 2: ❌ Erreur Phase 1
```
1. POST /api/invoice avec RN invalide
2. Phase 1: ✗ Erreur DGI (INVALID_DATA)
3. DB: status = "PENDING", errorCode = "INVALID_DATA", errorDesc = "..."
4. Client: Message d'erreur ✓
5. Phase 2: ← Skippée
```

### Scénario 3: ✓ Phase 1, ❌ Phase 2
```
1. POST /api/invoice avec nouvelle RN
2. Phase 1: ✓ Reçoit uid
3. DB: status = "PHASE1"
4. Phase 2: ✗ Timeout
5. DB: status = "PHASE1", errorCode = "DGI_PHASE2_ERROR"
6. Client: Message "Phase 1 OK, Phase 2 échouée" ✓
7. Client peut réessayer Phase 2 ultérieurement
```

### Scénario 4: Facture déjà soumise
```
1. POST /api/invoice avec RN existante (status=PHASE1)
2. Phase 1: ✗ Vérifie status = "PHASE1" → errorCode
3. DB: Pas de modification
4. Client: Message "Facture déjà soumise" ✓
```

---

## 🎓 Points Clés

### ✅ Avantages
1. **Entité unique**: Une seule source de vérité (DB)
2. **Traçabilité**: Tous les changements enregistrés
3. **Résilience**: Phase 1 persiste même si Phase 2 échoue
4. **Clarté**: Statuts et erreurs évidentes
5. **Flexibilité**: Peut continuer Phase 2 plus tard
6. **Logging**: Chaque phase loggée séparément
7. **Type-safe**: Pas de mélange DgiResponse + InvoiceEntity

### ⚠️ Points d'Attention
1. Transactions: Assurez-vous que `@Transactional` couvre tout
2. Retry: Les retries doivent gérer les mises à jour multiples
3. Idempotence: Gérer les appels dupliqués (check RN, UID)
4. Timeouts: DGI peut être lent, avoir des timeouts généreux

---

## 📋 Migration depuis Ancien Code

### Ancien
```java
DgiResponse resp = dgiService.submitInvoice(invoice, token);
if (resp.success) {
    invoice.uid = resp.uid;
    invoice.persist();
    return invoice;
}
```

### Nouveau ✅
```java
InvoiceEntity result = dgiService.submitInvoice(invoice, token);
// Plus besoin de gérer la persistance
// result contient déjà tous les données et statuts
return InvoiceEntityResponseMapper.toUserResponse(result);
```

---

## 🚀 Prochaines Étapes

1. ✅ Refactoriser DgiService (FAIT)
2. ✅ Améliorer InvoiceEntity (FAIT)
3. ✅ Créer InvoiceEntityResponseMapper (FAIT)
4. ⬜ Mettre à jour InvoiceResource (À faire)
5. ⬜ Ajouter les endpoints pour Phase 2 seule (Optionnel)
6. ⬜ Tester les 4 scénarios (À faire)
7. ⬜ Mettre à jour la doc Swagger (À faire)
