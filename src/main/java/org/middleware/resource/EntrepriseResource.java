package org.middleware.resource;


import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.middleware.models.Entreprise;
import org.middleware.service.JwtService;
import org.mindrot.jbcrypt.BCrypt;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
        public String rccm;
        public String adresse;
        public String telephone;
        public String nomMagasin;
        public String role;
        public String isf;
        public String token;
        public String dgiToken;
        public String password;

        // ✅ Ajoute ceci
        public UserDTO() {}
    }

    // -------------------------
    // CRUD
    // -------------------------

    @GET
    @Path("/all")
    public List<Entreprise> list2() {
        
        //
        List<Entreprise> entreprises = Entreprise.listAll();
        //
        return entreprises;
    }

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
        //
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

        // Entreprise usr = Entreprise.find("email", dto.email).firstResult();
        // if(usr != null) {
        //     return Response.status(409).entity("Utilisateur non trouvé").build();
        // }

        if (dto.email == null || dto.password == null || dto.nom == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Champs obligatoires manquants")
                .build();
        }

        if (dto.token == null || dto.token.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Le champ 'token' est obligatoire")
                .build();
        }

        Entreprise user = new Entreprise();
        user.role = dto.role;
        user.nif = dto.nif;
        user.rccm = dto.rccm;
        user.adresse = dto.adresse;
        user.telephone = dto.telephone;
        user.nomMagasin = dto.nomMagasin;
        user.email = dto.email;
        user.isf = dto.isf;
        user.password = BCrypt.hashpw(dto.password, BCrypt.gensalt());//dto.password;
        user.nom = dto.nom;
        user.dgiToken = dto.dgiToken != null ? dto.dgiToken : dto.token;
        user.token = dto.token;
        user.persist();

        HashMap data = buildAuthResponse(user);

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
        user.rccm = dto.rccm;
        user.adresse = dto.adresse;
        user.telephone = dto.telephone;
        user.nomMagasin = dto.nomMagasin;
        user.email = dto.email;
        user.isf = dto.isf;
        user.password = dto.password;
        user.nom = dto.nom;
        user.dgiToken = dto.dgiToken != null ? dto.dgiToken : user.dgiToken;

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
        HashMap data = buildAuthResponse(entreprise);

        return Response.ok(data).build();
    }

    private HashMap buildAuthResponse(Entreprise entreprise) {
        HashMap data = new HashMap();
        data.put("bridge_token", jwtService.generateJWT(entreprise));
        data.put("data", toUserData(entreprise));
        return data;
    }

    private HashMap toUserData(Entreprise entreprise) {
        HashMap user = new HashMap();
        user.put("id", entreprise.id != null ? entreprise.id.toString() : null);
        user.put("email", entreprise.email);
        user.put("nom", entreprise.nom);
        user.put("nif", entreprise.nif);
        user.put("rccm", entreprise.rccm);
        user.put("adresse", entreprise.adresse);
        user.put("telephone", entreprise.telephone);
        user.put("nomMagasin", entreprise.nomMagasin);
        user.put("role", entreprise.role);
        user.put("isf", entreprise.isf);
        user.put("token", entreprise.token);
        user.put("dgiToken", entreprise.dgiToken);
        return user;
    }

}
