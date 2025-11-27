package org.middleware.client;

import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;

@ApplicationScoped
public class DgiClientHeadersFactory implements ClientHeadersFactory {

    @Inject
    @ConfigProperty(name = "dgi.api.token", defaultValue = "")
    String dgiToken;

    @Override
    public MultivaluedMap<String, String> update(
            MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> outgoingHeaders) {

        outgoingHeaders.putSingle("Authorization", "Bearer " + dgiToken);
        outgoingHeaders.putSingle("Content-Type", "application/json");
        return outgoingHeaders;
    }
}
