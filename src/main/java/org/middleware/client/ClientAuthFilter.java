package org.middleware.client;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.middleware.models.ApiClient;
import org.middleware.service.ClientTokenService;

// @Provider
// @Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class ClientAuthFilter implements ContainerRequestFilter {

    @Inject
    ClientTokenService tokenService;

    @Override
    public void filter(ContainerRequestContext ctx) {

        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("q/") || path.equals("bridge/api/info/status")) return;

        String header = ctx.getHeaderString("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            abort(ctx, "Clé API manquante");
            return;
        }

        String token = header.substring(7);
        ApiClient client = tokenService.getClient(token);

        if (client == null) {
            abort(ctx, "Clé API invalide");
            return;
        }

        ctx.setProperty("client", client);
    }

    private void abort(ContainerRequestContext ctx, String msg) {
        ctx.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .entity(msg)
                        .build()
        );
    }
}
