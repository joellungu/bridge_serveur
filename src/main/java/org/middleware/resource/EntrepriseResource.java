package org.middleware.resource;


import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.middleware.models.Entreprise;
import org.middleware.service.JwtService;
import org.mindrot.jbcrypt.BCrypt;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/entreprises")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EntrepriseResource {


    @Inject
    JsonWebToken jwt;

    @Inject
    JwtService jwtService;

    // ========= DTO LOGIN =========
    public static class LoginRequest {
        public String email;
        public String password;
    }

    // ========= DTO CREATE/UPDATE =========
    public static class UserDTO {
        public String email;
        public String nom;
        public String nif;
        public String role;
        public String isf;
        public String token;
        public String password;
    }

    // -------------------------
    // CRUD
    // -------------------------

    @GET
    @RolesAllowed({"ADMIN"})
    public List<Entreprise> list() {
        //
        // Récupérer l'email depuis le token JWT
        String email = jwt.getClaim("email");
        if (email == null) {
            throw new NotAuthorizedException("Token invalide");
        }

        // Trouver l'utilisateur par email
        Entreprise usAdmin = Entreprise.find("email", email).firstResult();
        if (usAdmin == null) {
            throw new NotFoundException("Utilisateur non trouvé");
        }

        List<Entreprise> entreprises = Entreprise.listAll();
        //

        return entreprises;
    }

    @GET
    @Path("/{id}")
    public Entreprise get(@PathParam("id") UUID id) {
        return Entreprise.findById(id);
    }

    @GET
    @RolesAllowed("USER")
    @Path("/current")
    public Entreprise getAdmin() {
        String email = jwt.getClaim("email");
        if (email == null) {
            throw new NotAuthorizedException("Token invalide");
        }

        Entreprise user = Entreprise.find("email", email).firstResult();
        if (user == null) {
            throw new NotAuthorizedException("Utilisateur non trouvé");
        }

        return user;
    }

    @POST
    @Path("/save")
    @PermitAll
    @Transactional
    public Response create(UserDTO dto) {
        //, @Context SecurityContext securityContext
        //@RolesAllowed({"ADMIN"})

        if (Entreprise.find("email", dto.email).firstResult() != null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Email déjà utilisé")
                    .build();
        }


        /*
        // Récupérer l'email depuis le token JWT
        String email = jwt.getClaim("email");
        if (email == null) {
            throw new NotAuthorizedException("Token invalide");
        }
        */

        // Trouver l'utilisateur par email

        Entreprise usr = Entreprise.find("email", dto.email).firstResult();
        if(usr != null) {
            return Response.status(409).entity("Utilisateur non trouvé").build();
        }

        Entreprise user = new Entreprise();
        user.role = dto.role;
        user.nif = dto.nif;
        user.email = dto.email;
        user.isf = dto.isf;
        user.password = BCrypt.hashpw(dto.password, BCrypt.gensalt());//dto.password;
        user.nom = dto.nom;
        user.token = dto.token;
        user.persist();

        //jwtService.generateJWT(user);
        HashMap data = new HashMap();
        data.put("bridge_toke", jwtService.generateJWT(user));
        data.put("data", user);

        return Response.created(URI.create("/users/" + user.id))
                .entity(data)
                .build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Entreprise update(@PathParam("id") UUID id, UserDTO dto) {

        Entreprise user = Entreprise.findById(id);
        if (user == null) {
            throw new NotFoundException("Utilisateur non trouvé");
        }

        user.email = dto.email != null ? dto.email : user.email;
        user.role = dto.role;
        user.nif = dto.nif;
        user.email = dto.email;
        user.isf = dto.isf;
        user.password = dto.password;
        user.nom = dto.nom;
        user.token = jwtService.generateJWT(user);

        if (dto.password != null && !dto.password.isEmpty()) {
            user.password = BCrypt.hashpw(dto.password, BCrypt.gensalt());
        }

        return user;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") UUID id) {
        Entreprise.deleteById(id);
    }

    // -------------------------
    // LOGIN
    // -------------------------

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {

        Entreprise entreprise = Entreprise.find("email", request.email).firstResult();

        if (entreprise == null || !BCrypt.checkpw(request.password, entreprise.password)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Email ou mot de passe incorrect")
                    .build();
        }
        // ⚠️ IMPORTANT : ne jamais renvoyer le password hash
        entreprise.password = null;
        entreprise.token = jwtService.generateJWT(entreprise);

        return Response.ok(entreprise).build();
    }

}