package org.middleware.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.middleware.dto.*;

@Path("/")
@RegisterRestClient(configKey = "dgi.invoice")
//@ClientHeaders(DgiClientHeadersFactory.class)
@RegisterClientHeaders(DgiClientHeadersFactory.class)

public interface DgiInvoiceClient {

    @POST
    @Path("invoice")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    InvoiceResponseDataDto requestInvoice(InvoiceRequestDataDto req);

    @POST
    @Path("invoice/{uid}/{action}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    FinalizeInvoiceResponseDataDto finalizeOrCancel(
            @PathParam("uid") String uid,
            @PathParam("action") String action,
            FinalizeInvoiceRequestDataDto body);

    @GET
    @Path("invoice/{uid}")
    @Produces(MediaType.APPLICATION_JSON)
    InvoiceDetailsDto getInvoiceDetails(@PathParam("uid") String uid);
}