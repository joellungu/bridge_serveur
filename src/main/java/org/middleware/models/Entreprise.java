package org.middleware.models;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class Entreprise extends PanacheEntityBase {
    //
    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    public UUID id;
    //
    public String email;
    public String nom;
    public String nif;
    public String role;

    public String isf;

    @Column(name = "password", length = 255, nullable = false)
    public String password;

    @Column(name = "token", columnDefinition = "TEXT")
    public String token;
}
