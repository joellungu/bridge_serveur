package org.middleware.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.middleware.models.InvoiceEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DgiInvoiceRequestTest {

    @Test
    void mappingDoesNotExposeBridgeInternalFields() throws Exception {
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.id = UUID.randomUUID();
        invoice.email = "client@example.test";
        invoice.rn = "FV-2026-00001";
        invoice.nif = "A1234567";
        invoice.isf = "ISF-1";
        invoice.type = "FV";
        invoice.mode = "ht";
        invoice.currency = "CDF";
        invoice.status = "PENDING";
        invoice.errorCode = "SHOULD_NOT_LEAK";
        invoice.dgiToken = "secret";
        invoice.client = new InvoiceEntity.Client();
        invoice.client.name = "Client";
        invoice.client.type = "PP";
        InvoiceEntity.Item item = new InvoiceEntity.Item();
        item.name = "Service";
        item.type = "SER";
        item.price = BigDecimal.TEN;
        item.quantity = BigDecimal.ONE;
        item.taxGroup = "A";
        invoice.items = List.of(item);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode json = mapper.valueToTree(DgiInvoiceRequest.from(invoice));

        assertEquals("FV-2026-00001", json.get("rn").asText());
        assertFalse(json.has("id"));
        assertFalse(json.has("email"));
        assertFalse(json.has("status"));
        assertFalse(json.has("errorCode"));
        assertFalse(json.has("dgiToken"));
        assertFalse(json.has("createdAt"));
        assertFalse(json.has("updatedAt"));
    }
}
