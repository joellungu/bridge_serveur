package org.middleware.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.middleware.models.InvoiceEntity;

/**
 * Calcul « côté SFE » du total TTC et de la TVA d'une facture, selon les règles
 * DGI (tableau de conformité SFE v1.2, section 5 ; groupes FACNO v1.2).
 *
 * <p>Le protocole e-MCF exige que le SFE compare les totaux renvoyés par la
 * DGI (demande de facture) avec son propre calcul avant de confirmer, et
 * annule la demande en cas d'écart.</p>
 */
public final class DgiTotalsCalculator {

    /** Taux de TVA par groupe. Le groupe N est traité à part (montant = TVA). */
    private static final Map<String, BigDecimal> TAUX = Map.ofEntries(
            Map.entry("A", BigDecimal.ZERO),
            Map.entry("B", new BigDecimal("0.16")),
            Map.entry("C", new BigDecimal("0.05")),
            Map.entry("D", BigDecimal.ZERO),
            Map.entry("E", BigDecimal.ZERO),
            Map.entry("F", new BigDecimal("0.16")),
            Map.entry("G", new BigDecimal("0.05")),
            Map.entry("H", BigDecimal.ZERO),
            Map.entry("I", BigDecimal.ZERO),
            Map.entry("J", BigDecimal.ZERO),
            Map.entry("K", BigDecimal.ZERO),
            Map.entry("L", BigDecimal.ZERO),
            Map.entry("M", BigDecimal.ZERO),
            Map.entry("O", new BigDecimal("0.01")),
            Map.entry("P", new BigDecimal("0.01")));

    private DgiTotalsCalculator() {
    }

    /**
     * Résultat du calcul.
     *
     * @param total    total TTC attendu
     * @param tva      TVA totale attendue (groupe N compris, exigence 1.14)
     * @param fiable   faux si le calcul ne peut pas être garanti identique à
     *                 celui de la DGI (taxe spécifique, groupe inconnu, article
     *                 incomplet) : la comparaison est alors ignorée
     * @param raison   pourquoi le calcul n'est pas fiable
     */
    public record Totaux(BigDecimal total, BigDecimal tva, boolean fiable, String raison) {
    }

    public static Totaux calculer(InvoiceEntity invoice) {
        if (invoice == null || invoice.items == null || invoice.items.isEmpty()) {
            return new Totaux(BigDecimal.ZERO, BigDecimal.ZERO, false, "aucun article");
        }
        boolean ht = "ht".equalsIgnoreCase(invoice.mode == null ? "" : invoice.mode.trim());

        Map<String, BigDecimal> sommes = new LinkedHashMap<>();
        for (InvoiceEntity.Item item : invoice.items) {
            if (item == null || item.price == null || item.quantity == null) {
                return new Totaux(BigDecimal.ZERO, BigDecimal.ZERO, false, "article sans prix ou quantité");
            }
            boolean taxeSpecifique = (item.taxSpecificAmount != null && item.taxSpecificAmount.signum() != 0)
                    || (item.taxSpecificValue != null && !item.taxSpecificValue.isBlank());
            if (taxeSpecifique) {
                return new Totaux(BigDecimal.ZERO, BigDecimal.ZERO, false, "taxe spécifique");
            }
            String groupe = item.taxGroup == null ? "" : item.taxGroup.trim().toUpperCase(Locale.ROOT);
            if (!"N".equals(groupe) && !TAUX.containsKey(groupe)) {
                return new Totaux(BigDecimal.ZERO, BigDecimal.ZERO, false, "groupe de taxation inconnu " + groupe);
            }
            BigDecimal montant = item.price.multiply(item.quantity).setScale(2, RoundingMode.HALF_UP);
            sommes.merge(groupe, montant, BigDecimal::add);
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tva = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entree : sommes.entrySet()) {
            BigDecimal somme = entree.getValue().setScale(2, RoundingMode.HALF_UP);
            if ("N".equals(entree.getKey())) {
                // Exigence 5.8 : le montant du groupe N est entièrement de la TVA.
                total = total.add(somme);
                tva = tva.add(somme);
                continue;
            }
            BigDecimal taux = TAUX.get(entree.getKey());
            if (taux.signum() == 0) {
                total = total.add(somme);
            } else if (ht) {
                // Exigences 5.6 / 5.7 : taxe calculée sur la base du groupe,
                // arrondie au supérieur.
                BigDecimal taxe = somme.multiply(taux).setScale(2, RoundingMode.CEILING);
                total = total.add(somme).add(taxe);
                tva = tva.add(taxe);
            } else {
                BigDecimal taxe = somme.multiply(taux)
                        .divide(BigDecimal.ONE.add(taux), 10, RoundingMode.HALF_UP)
                        .setScale(2, RoundingMode.CEILING);
                total = total.add(somme);
                tva = tva.add(taxe);
            }
        }
        return new Totaux(total.setScale(2, RoundingMode.HALF_UP), tva.setScale(2, RoundingMode.HALF_UP), true, null);
    }

    /**
     * Compare les totaux renvoyés par la DGI au calcul du SFE.
     *
     * @return {@code null} si conformes (ou calcul non fiable), sinon la
     *         description de l'écart
     */
    public static String ecart(InvoiceEntity invoice, BigDecimal totalDgi, BigDecimal tvaDgi, BigDecimal tolerance) {
        Totaux attendu = calculer(invoice);
        if (!attendu.fiable()) {
            return null;
        }
        BigDecimal t = totalDgi == null ? BigDecimal.ZERO : totalDgi;
        BigDecimal v = tvaDgi == null ? BigDecimal.ZERO : tvaDgi;
        boolean ecartTotal = t.subtract(attendu.total()).abs().compareTo(tolerance) > 0;
        boolean ecartTva = v.subtract(attendu.tva()).abs().compareTo(tolerance) > 0;
        if (!ecartTotal && !ecartTva) {
            return null;
        }
        return String.format(Locale.ROOT,
                "Totaux DGI (TTC %s, TVA %s) différents du calcul SFE (TTC %s, TVA %s) en mode %s : "
                        + "demande annulée avant normalisation. Vérifiez MODE, ITEM_TAX_GROUP, ITEM_PRICE et ITEM_QUANTITY.",
                t.toPlainString(), v.toPlainString(),
                attendu.total().toPlainString(), attendu.tva().toPlainString(),
                invoice.mode == null ? "?" : invoice.mode);
    }
}
