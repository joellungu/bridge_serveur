package org.middleware.resource;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.middleware.dto.ApiResponse;
import org.middleware.models.ApiClient;
import org.middleware.models.Entreprise;
import org.middleware.service.DgiService;

@Path("/api/info")
@Produces(MediaType.APPLICATION_JSON)
public class DgiInfoResource {

    @Context
    ContainerRequestContext requestContext;

    @GET
    @Path("/status")
    @PermitAll
    public Response status() {
        try {
            Entreprise entreprise = getAuthenticatedEntreprise();
            if (entreprise == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("API_CLIENT_NOT_LINKED",
                                "Cle API valide mais aucune entreprise liee"))
                        .build();
            }

            String dgiToken = entreprise.dgiToken != null && !entreprise.dgiToken.isBlank()
                    ? entreprise.dgiToken
                    : entreprise.token;
            if (dgiToken == null || dgiToken.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("DGI_TOKEN_REQUIRED",
                                "Aucun token DGI configure pour cette entreprise"))
                        .build();
            }

            DgiService dgiService = CDI.current().select(DgiService.class).get();
            JsonNode dgiStatus = dgiService.getInfoStatus(dgiToken);
            return Response.ok(dgiStatus).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("DGI_INFO_ERROR",
                            "Erreur lors de la verification du statut DGI: " + e.getMessage()))
                    .build();
        }
    }

    private Entreprise getAuthenticatedEntreprise() {
        Object clientProperty = requestContext != null ? requestContext.getProperty("client") : null;
        if (clientProperty instanceof ApiClient apiClient && apiClient.nif != null && !apiClient.nif.isBlank()) {
            return Entreprise.find("nif", apiClient.nif).firstResult();
        }
        return null;
    }
}
