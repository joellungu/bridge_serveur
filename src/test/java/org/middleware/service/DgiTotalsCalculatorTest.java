package org.middleware.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.middleware.models.InvoiceEntity;

class DgiTotalsCalculatorTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1.00");

    private static InvoiceEntity facture(String mode, Object... articles) {
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.rn = "2026/0001";
        invoice.mode = mode;
        invoice.items = new ArrayList<>();
        for (int i = 0; i < articles.length; i += 3) {
            InvoiceEntity.Item item = new InvoiceEntity.Item();
            item.taxGroup = (String) articles[i];
            item.price = new BigDecimal(articles[i + 1].toString());
            item.quantity = new BigDecimal(articles[i + 2].toString());
            invoice.items.add(item);
        }
        return invoice;
    }

    @Test
    void ttcGroupeB() {
        DgiTotalsCalculator.Totaux t = DgiTotalsCalculator.calculer(facture("ttc", "B", 116, 1));
        assertTrue(t.fiable());
        assertEquals(new BigDecimal("116.00"), t.total());
        assertEquals(new BigDecimal("16.00"), t.tva());
    }

    @Test
    void htGroupeCEtExonere() {
        DgiTotalsCalculator.Totaux t = DgiTotalsCalculator.calculer(facture("ht", "C", 50, 2, "A", 30, 1));
        assertEquals(new BigDecimal("135.00"), t.total());
        assertEquals(new BigDecimal("5.00"), t.tva());
    }

    @Test
    void groupeNEstEntierementDeLaTva() {
        DgiTotalsCalculator.Totaux t = DgiTotalsCalculator.calculer(facture("ttc", "M", 100, 1, "N", 30, 1));
        assertEquals(new BigDecimal("130.00"), t.total());
        assertEquals(new BigDecimal("30.00"), t.tva());
    }

    @Test
    void totauxConformesPasDEcart() {
        InvoiceEntity f = facture("ttc", "B", 116, 1);
        assertNull(DgiTotalsCalculator.ecart(f, new BigDecimal("116"), new BigDecimal("16"), TOLERANCE));
    }

    @Test
    void ecartDetecteSiModeIncoherent() {
        // Prix saisis HT mais la DGI a calculé comme si c'était du TTC (ou inversement).
        InvoiceEntity f = facture("ht", "B", 100, 1);
        String ecart = DgiTotalsCalculator.ecart(f, new BigDecimal("100"), new BigDecimal("13.79"), TOLERANCE);
        assertNotNull(ecart);
        assertTrue(ecart.contains("annulée"));
    }

    @Test
    void taxeSpecifiqueRendLeCalculNonFiable() {
        InvoiceEntity f = facture("ttc", "B", 116, 1);
        f.items.get(0).taxSpecificValue = "10%";
        assertFalse(DgiTotalsCalculator.calculer(f).fiable());
        // Calcul non fiable : on ne bloque pas la facture sur une différence.
        assertNull(DgiTotalsCalculator.ecart(f, new BigDecimal("999"), BigDecimal.ZERO, TOLERANCE));
    }
}
