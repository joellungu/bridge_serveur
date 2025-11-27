package org.middleware.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.middleware.models.ApiClient;
import org.middleware.repository.ApiClientRepository;

@ApplicationScoped
public class ClientTokenService {

    @Inject
    ApiClientRepository repository;

    public ApiClient getClient(String token) {
        return repository.findValidClient(token);
    }
}
