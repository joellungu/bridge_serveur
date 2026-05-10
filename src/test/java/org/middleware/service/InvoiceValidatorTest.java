package org.middleware.service;

import org.junit.jupiter.api.Test;
import org.middleware.models.InvoiceEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceValidatorTest {

    private final InvoiceValidator validator = new InvoiceValidator();

    @Test
    void rejectsMissingRequiredFields() {
        List<String> errors = validator.validateForDgi(new InvoiceEntity());

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("rn")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("article")));
    }

    @Test
    void rejectsCreditNoteWithoutReference() {
        InvoiceEntity invoice = validInvoice();
        invoice.type = "AV";

        List<String> errors = validator.validateForDgi(invoice);

        assertTrue(errors.stream().anyMatch(error -> error.contains("avoir")));
    }

    @Test
    void acceptsValidInvoice() {
        List<String> errors = validator.validateForDgi(validInvoice());

        assertTrue(errors.isEmpty());
    }

    private InvoiceEntity validInvoice() {
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.rn = "FV-2026-00001";
        invoice.type = "FV";
        invoice.mode = "ht";
        invoice.nif = "A1234567";
        invoice.isf = "ISF-1";
        invoice.currency = "CDF";
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
        return invoice;
    }
}
