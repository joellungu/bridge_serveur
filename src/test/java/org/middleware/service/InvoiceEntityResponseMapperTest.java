package org.middleware.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.middleware.models.InvoiceEntity;

public class InvoiceEntityResponseMapperTest {

    private InvoiceEntity phase1(String uid, String errorCode, String errorDesc) {
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.rn = "F001";
        invoice.status = "PHASE1";
        invoice.uid = uid;
        invoice.errorCode = errorCode;
        invoice.errorDesc = errorDesc;
        return invoice;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void phase1KeepsPhase2ErrorWithoutLosingUid() {
        Map<String, Object> response = InvoiceEntityResponseMapper.toUserResponse(
                phase1("UID-1", "DGI_PHASE2_ERROR", "Confirmation impossible"));
        assertEquals(true, response.get("success"));
        assertEquals("UID-1", response.get("uid"));
        assertNotNull(response.get("submission"));
        assertNotNull(response.get("nextStep"));
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertNotNull(error, "l'erreur de phase 2 doit rester disponible");
        assertEquals("DGI_PHASE2_ERROR", error.get("code"));
        assertEquals("Confirmation impossible", error.get("description"));
    }

    @Test
    public void phase1WithoutErrorHasNoErrorBlock() {
        Map<String, Object> response = InvoiceEntityResponseMapper.toUserResponse(
                phase1("UID-2", null, null));
        assertEquals(true, response.get("success"));
        assertNull(response.get("error"));
    }
}
