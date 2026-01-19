# 📊 Guide de Refactorisation InvoiceResource avec InvoiceEntity Retournable

## 🎯 Principes

### Avant (Ancien)
```java
// Service retourne DgiResponse
DgiResponse dgiResponse = dgiService.submitInvoice(invoice, token);

// Resource doit fusionner les données
if (dgiResponse.success) {
    invoice.uid = dgiResponse.uid;
    invoice.status = dgiResponse.status;
    invoice.persist();
    return invoice; // Mélange de logiques
}
```

### Après (Nouveau) ✅
```java
// Service retourne InvoiceEntity mise à jour et sauvegardée
InvoiceEntity updated = dgiService.submitInvoice(invoice, token);

// Resource retourne simplement l'entité
return Response.ok(InvoiceEntityResponseMapper.toUserResponse(updated)).build();
```

---

## 📋 Implémentation dans InvoiceResource

### Méthode POST `/api/invoice` - Soumission complète

```java
@POST
@RolesAllowed({"ADMIN","USER"})
@Transactional
public Response requestInvoice(InvoiceEntity invoice) {
    try {
        // 1. Validation de base
        if (invoice.rn == null || invoice.rn.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("RN_REQUIRED",
                            "Le numéro de facture (RN) est obligatoire"))
                    .build();
        }

        // 2. Récupération de l'utilisateur depuis le JWT
        String email = jwt.getClaim("email");
        if (email == null || email.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("EMAIL_NOT_FOUND",
                            "Aucun email trouvé dans le token"))
                    .build();
        }

        Entreprise entreprise = Entreprise.find("email", email).firstResult();
        if (entreprise == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("USER_NOT_FOUND",
                            "Aucun utilisateur trouvé pour cet email"))
                    .build();
        }

        // 3. Vérification si la facture existe
        InvoiceEntity existingInvoice = InvoiceEntity.find("rn", invoice.rn).firstResult();

        // 4. Initialisation ou récupération de la facture
        InvoiceEntity invoiceToProcess;
        if (existingInvoice == null) {
            // Nouvelle facture
            invoice.id = null;
            invoice.email = email;
            invoice.nif = entreprise.nif;
            invoice.isf = entreprise.isf;
            invoice.status = "PENDING";
            invoice.persist();
            invoiceToProcess = invoice;
        } else {
            // Facture existante - Vérifier si elle peut être retraitée
            if ("CONFIRMED".equals(existingInvoice.status)) {
                return Response.status(200)
                        .entity(ApiResponse.error("INVOICE_ALREADY_CONFIRMED",
                                "Cette facture a déjà été confirmée"))
                        .build();
            }
            invoiceToProcess = existingInvoice;
        }

        // 5. Soumission à la DGI (PHASE 1 + PHASE 2)
        DgiService dgiService = CDI.current().select(DgiService.class).get();
        InvoiceEntity processedInvoice = dgiService.submitInvoice(invoiceToProcess, entreprise.token);

        // 6. Retour structuré au client
        return Response.ok(
                InvoiceEntityResponseMapper.toUserResponse(processedInvoice)
        ).build();

    } catch (Exception e) {
        LOG.log(Level.SEVERE, "Erreur traitement facture: " + e.getMessage(), e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("INTERNAL_ERROR",
                        "Erreur interne: " + e.getMessage()))
                .build();
    }
}
```

---

### Méthode POST `/api/invoice/{uid}/confirm` - Phase 2 Uniquement (Futur)

```java
@POST
@Path("{uid}/confirm")
@RolesAllowed({"ADMIN","USER"})
@Transactional
public Response confirmInvoice(@PathParam("uid") String uid) {
    try {
        String email = jwt.getClaim("email");
        Entreprise entreprise = Entreprise.find("email", email).firstResult();
        
        // Récupérer la facture avec ce UID
        InvoiceEntity invoice = InvoiceEntity.find("email = ?1 and uid = ?2", email, uid)
                .firstResult();
        
        if (invoice == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("INVOICE_NOT_FOUND",
                            "Facture non trouvée"))
                    .build();
        }

        // Confirmer Phase 2
        DgiService dgiService = CDI.current().select(DgiService.class).get();
        InvoiceEntity confirmedInvoice = dgiService.confirmInvoicePhase2(invoice, entreprise.token);

        // Retour structuré
        return Response.ok(
                InvoiceEntityResponseMapper.toUserResponse(confirmedInvoice)
        ).build();

    } catch (Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("INTERNAL_ERROR", e.getMessage()))
                .build();
    }
}
```

---

## 📤 Exemples de Réponses

### Succès Complet (CONFIRMED)
```json
{
  "invoiceNumber": "FAC001",
  "status": "CONFIRMED",
  "uid": "DGI-12345678",
  "success": true,
  "message": "✓ Facture validée et confirmée par la DGI",
  "submission": {
    "uid": "DGI-12345678",
    "total": 1000.00,
    "curTotal": 1000.00,
    "vtotal": 200.00,
    "status": "PHASE1"
  },
  "confirmation": {
    "qrCode": "00000000000000000000...",
    "dateTime": "2025-01-18T14:30:00",
    "codeDEFDGI": "DEF-2025-001",
    "counters": "0001",
    "nim": "NIM123",
    "status": "CONFIRMED"
  }
}
```

### Phase 1 Succès, En Attente de Phase 2
```json
{
  "invoiceNumber": "FAC002",
  "status": "PHASE1",
  "uid": "DGI-87654321",
  "success": true,
  "message": "⏳ Facture soumise avec succès. En attente de confirmation.",
  "submission": {
    "uid": "DGI-87654321",
    "total": 500.00,
    "curTotal": 500.00,
    "vtotal": 100.00,
    "status": "PHASE1"
  },
  "nextStep": {
    "phase": "2",
    "action": "Confirmation de la facture",
    "uid": "DGI-87654321"
  }
}
```

### Erreur Phase 1
```json
{
  "invoiceNumber": "FAC003",
  "status": "PENDING",
  "uid": null,
  "success": false,
  "message": "✗ Erreur lors du traitement",
  "error": {
    "code": "INVALID_DATA",
    "description": "Le numéro de facture est déjà utilisé",
    "status": "PENDING"
  }
}
```

### Erreur Phase 2 (mais Phase 1 OK)
```json
{
  "invoiceNumber": "FAC004",
  "status": "PHASE1",
  "uid": "DGI-11111111",
  "success": false,
  "message": "✗ Erreur lors du traitement",
  "submission": {
    "uid": "DGI-11111111",
    "total": 750.00,
    "curTotal": 750.00,
    "vtotal": 150.00
  },
  "error": {
    "code": "DGI_PHASE2_ERROR",
    "description": "Timeout lors de la confirmation",
    "status": "PHASE1"
  }
}
```

---

## 🔄 Flux Détaillé

### Cas 1: Nouvelle Facture → Succès Complet

```
1. POST /api/invoice avec RN=FAC001
   ↓
2. Resource vérifie RN unique
   ↓
3. Resource crée InvoiceEntity avec status=PENDING
   ↓
4. dgiService.submitInvoice(invoice, token)
   ├─ Phase 1: submitInvoiceToDgi()
   │  └─ invoice.status = "PHASE1"
   │  └─ invoice.uid = "DGI-xxx"
   │  └─ invoice.persist() ✓
   │
   └─ Phase 2: confirmInvoiceWithDgi()
      └─ invoice.status = "CONFIRMED"
      └─ invoice.qrCode = "..."
      └─ invoice.persist() ✓
   ↓
5. Return InvoiceEntity mise à jour
   ↓
6. Resource formatte avec InvoiceEntityResponseMapper
   ↓
7. Client reçoit réponse CONFIRMED avec QR Code ✓
```

### Cas 2: Nouvelle Facture → Phase 1 OK, Phase 2 Erreur

```
1. POST /api/invoice avec RN=FAC002
   ↓
2. dgiService.submitInvoice()
   ├─ Phase 1: ✓ Succès
   │  └─ invoice.status = "PHASE1"
   │  └─ invoice.persist() ✓
   │
   └─ Phase 2: ✗ Erreur
      └─ invoice.errorCode = "DGI_PHASE2_ERROR"
      └─ invoice.errorDesc = "Timeout..."
      └─ invoice.status = "PHASE1" (reste en Phase 1)
      └─ invoice.persist() ✓
   ↓
3. Return InvoiceEntity avec errorCode/errorDesc
   ↓
4. Client reçoit réponse PHASE1 + erreur
   ↓
5. Client peut réessayer Phase 2 avec l'UID reçu ✓
```

### Cas 3: Facture Existante en PHASE1 → Réessai Phase 2

```
1. POST /api/invoice/DGI-12345/confirm
   ↓
2. Resource récupère la facture existante
   └─ Status: PHASE1, UID: DGI-12345
   ↓
3. dgiService.confirmInvoicePhase2(invoice, token)
   └─ Phase 2: confirmInvoiceWithDgi()
      ├─ Si succès:
      │  └─ invoice.status = "CONFIRMED"
      │  └─ invoice.persist() ✓
      │
      └─ Si erreur:
         └─ invoice.errorCode = "..."
         └─ invoice.status = "PHASE1" (reste)
         └─ invoice.persist() ✓
   ↓
4. Return InvoiceEntity mise à jour
   ↓
5. Client reçoit le statut mis à jour ✓
```

---

## ✅ Avantages de Cette Approche

1. **Séparation claire**: Service persiste, Resource formatte
2. **Moins d'erreurs**: Une seule source de vérité (InvoiceEntity)
3. **Traçabilité**: Tous les statuts et erreurs en DB
4. **Résilience**: En cas d'erreur Phase 2, Phase 1 est sauvegardée
5. **Flexibilité**: Possibilité de confirmer ultérieurement
6. **UX améliorée**: Messages clairs au client

---

## 📝 Code à Supprimer/Remplacer dans InvoiceResource

### À Supprimer:
```java
// Anciens mappers - Plus nécessaires
InvoiceResponseMapper
InvoiceSubmissionResponse (peut être gardé si utilisé ailleurs)

// Anciennes méthodes - Plus nécessaires
processusDemande()
processusFinal()
handleExistingInvoice()
handleNewInvoice()
processDgiResponse()
```

### À Garder/Améliorer:
```java
// updateInvoiceWithCompanyInfo() - Toujours utile
// getPending() - À améliorer pour retourner la réponse formatée
```

---

## 🧪 Test Manual

### Succès Complet
```bash
curl -X POST http://localhost:9090/api/invoice \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "rn": "FAC001",
    "total": 1000,
    "vtotal": 200,
    ...
  }'

# Réponse attendue: status = CONFIRMED
```

### Erreur Phase 1
```bash
curl -X POST http://localhost:9090/api/invoice \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "rn": "FAC001",  # RN invalide ou déjà existant
    ...
  }'

# Réponse attendue: errorCode + errorDesc
```

---

## 🎯 Checklist

- ✅ DgiService retourne InvoiceEntity mise à jour
- ✅ InvoiceEntity a @Column sur errorCode et errorDesc
- ✅ submitInvoicePhase1() persiste après Phase 1
- ✅ confirmInvoicePhase2() persiste après Phase 2
- ✅ submitInvoice() gère les deux phases
- ✅ InvoiceEntityResponseMapper formate les réponses
- ⬜ Mettre à jour InvoiceResource pour utiliser le nouveau flow
- ⬜ Tester les trois cas d'erreur
- ⬜ Mettre à jour les tests IT
