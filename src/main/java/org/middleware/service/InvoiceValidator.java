package org.middleware.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.middleware.models.InvoiceEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class InvoiceValidator {

    public List<String> validateForDgi(InvoiceEntity invoice) {
        List<String> errors = new ArrayList<>();

        if (invoice == null) {
            errors.add("La facture est obligatoire");
            return errors;
        }

        requireText(errors, invoice.rn, "Le numéro de facture (rn) est obligatoire");
        requireText(errors, invoice.type, "Le type de facture est obligatoire");
        requireText(errors, invoice.mode, "Le mode de prix est obligatoire");
        requireText(errors, invoice.nif, "Le NIF vendeur est obligatoire");
        requireText(errors, invoice.isf, "L'ISF est obligatoire");
        requireText(errors, invoice.currency, "La devise est obligatoire");

        if (invoice.mode != null && !isOneOf(invoice.mode.toLowerCase(), "ht", "ttc")) {
            errors.add("Le mode doit être 'ht' ou 'ttc'");
        }

        if (invoice.client == null) {
            errors.add("Le client est obligatoire");
        } else {
            requireText(errors, invoice.client.name, "Le nom du client est obligatoire");
            requireText(errors, invoice.client.type, "Le type du client est obligatoire");
        }

        if (invoice.items == null || invoice.items.isEmpty()) {
            errors.add("La facture doit contenir au moins un article");
        } else {
            for (int i = 0; i < invoice.items.size(); i++) {
                InvoiceEntity.Item item = invoice.items.get(i);
                String prefix = "Article " + (i + 1) + ": ";
                requireText(errors, item.name, prefix + "le nom est obligatoire");
                requireText(errors, item.type, prefix + "le type est obligatoire");
                requireText(errors, item.taxGroup, prefix + "le groupe de taxe est obligatoire");
                requirePositive(errors, item.price, prefix + "le prix doit être supérieur à 0");
                requirePositive(errors, item.quantity, prefix + "la quantité doit être supérieure à 0");

                if (item.type != null && !isOneOf(item.type.toUpperCase(), "BIE", "SER")) {
                    errors.add(prefix + "le type doit être BIE ou SER");
                }
            }
        }

        if ("AV".equalsIgnoreCase(invoice.type)) {
            requireText(errors, invoice.reference, "La référence de la facture originale est obligatoire pour un avoir");
            requireText(errors, invoice.referenceType, "Le type de référence est obligatoire pour un avoir");
        }

        if (hasText(invoice.curCode)) {
            if (invoice.curDate == null) {
                errors.add("La date de change est obligatoire si curCode est renseigné");
            }
            requirePositive(errors, invoice.curRate, "Le taux de change doit être supérieur à 0 si curCode est renseigné");
        }

        return errors;
    }

    private void requireText(List<String> errors, String value, String message) {
        if (!hasText(value)) {
            errors.add(message);
        }
    }

    private void requirePositive(List<String> errors, BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isOneOf(String value, String... allowedValues) {
        for (String allowed : allowedValues) {
            if (allowed.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
