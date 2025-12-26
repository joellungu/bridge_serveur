package org.middleware.service;



import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.middleware.models.Entreprise;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class JwtService {

    public String generateJWT(Entreprise entreprise) {
        return Jwt.issuer("bridge")
            .subject(entreprise.id.toString())
            .groups(Set.of(entreprise.role))
            //.groups(new HashSet<>(Arrays.asList("USER", "ADMIN")))
            .claim("email", entreprise.email)
            .expiresIn(31536000) // 1 an
        .sign();
        //31536000
    }
    //
}
