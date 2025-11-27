package org.middleware.client;

import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.middleware.dto.InfoResponseDto;

@Path("/")
@RegisterRestClient(configKey = "dgi.info")
//@ClientHeaders(DgiClientHeadersFactory.class)
@RegisterClientHeaders(DgiClientHeadersFactory.class)
public interface DgiInfoClient {

    @GET
    @Path("info/status")
    @Produces(MediaType.APPLICATION_JSON)
    InfoResponseDto getInfoStatus();

    @GET
    @Path("info/taxGroups")
    @Produces(MediaType.APPLICATION_JSON)
    Object getTaxGroups();
}
