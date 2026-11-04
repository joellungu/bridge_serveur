package org.middleware.service;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.middleware.models.InvoiceEntity;

import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Normalise une facture à la fois, chacune dans sa propre transaction.
 *
 * <p>Avant : tout un fichier était traité dans une seule transaction, avec deux
 * appels DGI par facture. Si la requête dépassait le délai (30 s chez Heroku,
 * 60 s de transaction par défaut), la base était annulée alors que la DGI
 * avait déjà normalisé les factures : les éléments de sécurité étaient perdus
 * et une relance produisait des doublons.</p>
 *
 * <p>Maintenant : chaque facture est enregistrée dès que la DGI a répondu,
 * et une relance retrouve toujours les factures déjà confirmées.</p>
 */
@ApplicationScoped
public class InvoiceNormalizationService {

    private static final Logger LOG = Logger.getLogger(InvoiceNormalizationService.class.getName());

    @Inject
    DgiService dgiService;

    /**
     * @param invoice  facture lue dans le fichier (nouvel objet, non persisté)
     * @param nif      NIF de l'entreprise connectée
     * @param dgiToken jeton e-MCF de l'entreprise
     * @return la facture à renvoyer au client : celle déjà confirmée pour ce RN,
     *         ou la nouvelle tentative avec son résultat DGI
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @TransactionConfiguration(timeout = 300)
    public InvoiceEntity normaliser(InvoiceEntity invoice, String nif, String dgiToken) {
        // 1. Déjà normalisée : on ne la renvoie jamais à la DGI.
        InvoiceEntity confirmee = InvoiceEntity.findConfirmed(nif, invoice.rn);
        if (confirmee != null) {
            chargerCollections(confirmee);
            return confirmee;
        }

        // 2. Une tentative précédente est encore en cours (requête coupée côté
        //    client, double clic…) : on ne crée pas une seconde demande.
        InvoiceEntity enCours = InvoiceEntity.findInFlight(nif, invoice.rn);
        if (enCours != null) {
            invoice.status = "REJECTED";
            invoice.errorCode = "EN_COURS";
            invoice.errorDesc = "Un envoi précédent de cette facture est encore en cours de traitement. "
                    + "Attendez " + InvoiceEntity.MINUTES_EN_COURS + " minutes puis relancez.";
            return invoice;
        }

        // 3. Nouvelle tentative : enregistrée puis envoyée (demande + contrôle
        //    des totaux + confirmation).
        try {
            invoice.persist();
            return dgiService.submitInvoice(invoice, dgiToken);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erreur lors de la normalisation de RN=" + invoice.rn, e);
            invoice.status = "ERROR";
            invoice.errorCode = "DGI_PHASE1_ERROR";
            invoice.errorDesc = "Exception lors de l'envoi à la DGI: " + e.getMessage();
            if (invoice.isPersistent()) {
                invoice.persist();
            }
            return invoice;
        }
    }

    /** Charge les collections avant la fin de la transaction (réponse JSON). */
    private static void chargerCollections(InvoiceEntity invoice) {
        if (invoice.items != null) {
            invoice.items.size();
        }
        if (invoice.payments != null) {
            invoice.payments.size();
        }
    }
}
