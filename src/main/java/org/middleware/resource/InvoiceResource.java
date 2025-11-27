package org.middleware.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.middleware.dto.*;
import org.middleware.models.ApiClient;
import org.middleware.service.DgiBridgeService;

import java.util.Map;

@Path("/api/invoice")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InvoiceResource {

    @Inject
    DgiBridgeService bridgeService;

    @Context
    ContainerRequestContext requestContext;

    private ApiClient getClient() {
        return (ApiClient) requestContext.getProperty("client");
    }

    @POST
    @Operation(summary = "Soumettre une demande de facture via le bridge")
    @APIResponse(responseCode = "200", description = "Réponse DGI (uid, totaux...)")
    public Response requestInvoice(InvoiceRequestDataDto req) {
        try {

            ApiClient client = getClient();

            // Forcer le NIF venant du client authentifié
            req.setNif(client.nif);

            InvoiceResponseDataDto resp = bridgeService.forwardInvoice(req);
            return Response.ok(resp).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Bridge error", "detail", e.getMessage())).build();
        }
    }

    @POST
    @Path("{uid}/confirm")
    @Operation(summary = "Confirmer / finaliser une facture via le bridge")
    public Response confirmInvoice(@PathParam("uid") String uid,
                                   FinalizeInvoiceRequestDataDto body) {

        ApiClient client = getClient();
        // Optionnel : audit, log, contrôle

        FinalizeInvoiceResponseDataDto resp =
                bridgeService.finalizeInvoice(uid, body);

        return Response.ok(resp).build();
    }

    @POST
    @Path("{uid}/cancel")
    @Operation(summary = "Annuler une facture via le bridge")
    public Response cancelInvoice(@PathParam("uid") String uid) {

        ApiClient client = getClient();
        // Optionnel : vérifier droits, log

        FinalizeInvoiceResponseDataDto resp =
                bridgeService.cancelInvoice(uid);

        return Response.ok(resp).build();
    }

    @GET
    @Path("{uid}")
    @Operation(summary = "Récupérer les détails d'une facture")
    public Response getPending(@PathParam("uid") String uid) {

        ApiClient client = getClient();
        // Optionnel : filtrer par entreprise

        InvoiceDetailsDto details =
                bridgeService.getPendingDetails(uid);

        return Response.ok(details).build();
    }

}
