package org.middleware.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.middleware.client.DgiInvoiceClient;
import org.middleware.dto.*;

@ApplicationScoped
public class DgiBridgeService {

    private static final Logger LOG = Logger.getLogger(DgiBridgeService.class);

    @Inject
    @RestClient
    DgiInvoiceClient invoiceClient;

    public InvoiceResponseDataDto forwardInvoice(InvoiceRequestDataDto req) {
        // minimal validation (à renforcer selon le PDF)
        if (req.getNif() == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("nif and items are required");
        }
        LOG.infov("Forwarding invoice for NIF: {0}", req.getNif());
        return invoiceClient.requestInvoice(req);
    }

    public FinalizeInvoiceResponseDataDto finalizeInvoice(String uid, FinalizeInvoiceRequestDataDto body) {
        return invoiceClient.finalizeOrCancel(uid, "CONFIRM", body);
    }

    public FinalizeInvoiceResponseDataDto cancelInvoice(String uid) {
        return invoiceClient.finalizeOrCancel(uid, "CANCEL", null);
    }

    public InvoiceDetailsDto getPendingDetails(String uid) {
        return invoiceClient.getInvoiceDetails(uid);
    }
}
