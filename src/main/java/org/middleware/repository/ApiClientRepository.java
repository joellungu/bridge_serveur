package org.middleware.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.middleware.models.ApiClient;

import java.time.LocalDateTime;

@ApplicationScoped
public class ApiClientRepository implements PanacheRepository<ApiClient> {

    public ApiClient findValidClient(String token) {
        return find("apiKey = ?1 and active = true", token)
                .firstResult();
    }
}
