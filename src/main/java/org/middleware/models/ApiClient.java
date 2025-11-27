package org.middleware.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_clients")
public class ApiClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String apiKey;

    public String companyName;

    public String nif;

    public boolean active = true;

    public LocalDateTime expiresAt;
}