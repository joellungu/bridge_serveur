package org.middleware.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.middleware.dto.*;
import org.middleware.models.Entreprise;
import org.middleware.models.InvoiceEntity;
import org.middleware.service.DgiBridgeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Path("/api/invoice")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InvoiceResource {

    @Inject
    DgiBridgeService bridgeService;

    @Context
    ContainerRequestContext requestContext;

    @Inject
    JsonWebToken jwt;

    HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) // Java 21+
            .build();

    @GET
    @Path("test")
    @RolesAllowed({"ADMIN","USER"})
    public String test(){
        return "Ok";
    }

    @GET
    @Path("/debug")
    @RolesAllowed({"USER", "ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject debug(@Context SecurityContext ctx) {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        // Subject
        if (ctx.getUserPrincipal() != null) {
            builder.add("subject", ctx.getUserPrincipal().getName());
        }

        // Rôles
        builder.add("isUserInRole USER", ctx.isUserInRole("USER"));
        builder.add("isUserInRole ADMIN", ctx.isUserInRole("ADMIN"));

        // Claims via injection (si vous utilisez CDI)
        return builder.build();
    }

    @POST
    @RolesAllowed({"ADMIN","USER"})
    @Transactional
    //@Operation(summary = "Soumettre une demande de facture via le bridge")
    //@APIResponse(responseCode = "200", description = "Réponse DGI (uid, totaux...)")
    public Response requestInvoice(InvoiceEntity invoice) {
        try {
            //
            invoice.rn = invoice.rn +"E";
            // Récupérer l'email depuis le token JWT
            String email = jwt.getClaim("email");
            //
            if(email == null || email.isEmpty()){
                return Response.status(405).entity("Aucun email trouvé").build();
            }
            //
            Entreprise entreprise = Entreprise.find("email", email).firstResult();
            //
            if(entreprise == null){
                return Response.status(405).entity("Aucun utilisateur trouvé").build();
            }
            //
            InvoiceEntity invoiceEntity = InvoiceEntity.find("rn", invoice.rn).firstResult();
            //
            if(invoiceEntity != null){
                if(invoiceEntity.status.equals("PENDING")) {
                    //
                    invoiceEntity.isf = entreprise.isf;
                    invoiceEntity.email = entreprise.email;
                    invoiceEntity.nif = entreprise.nif;
                    invoiceEntity.rn = invoice.rn +"E";
                    //
                    System.out.println("Le token :" + entreprise.token);
                    System.out.println("Le email :" + email);
                    //La logique finale...
                    //On retourne normalement : InvoiceEntity
                    JsonNode code = processusDemande(invoiceEntity, entreprise.token);//
                    //
                    if (code.get("errorDesc") != null) {
                        invoiceEntity.errorCode = code.get("errorCode").asText();
                        invoiceEntity.errorDesc = code.get("errorDesc").asText();
                        invoice.status = "PROBLEM";
                    } else {
                        //"total":0.0,"curTotal":0.0,"vtotal":0.0
                        invoiceEntity.uid = code.get("uid").asText();
                        invoiceEntity.total = BigDecimal.valueOf(code.get("total").asDouble());
                        invoiceEntity.curTotal = BigDecimal.valueOf(code.get("curTotal").asDouble());
                        invoiceEntity.vtotal = BigDecimal.valueOf(code.get("vtotal").asDouble());
                        //
                        JsonNode jn2 = processusFinal(invoiceEntity, entreprise.token);
                        //JsonNode jn2 = processusFinal(invoice, entreprise.token);
                        if(jn2.get("errorDesc") != null){
                            //
                            invoiceEntity.errorCode = jn2.get("errorCode").asText();
                            invoiceEntity.errorDesc = jn2.get("errorDesc").asText();
                            invoice.status = "PROBLEM";
                        } else {
                            invoiceEntity.qrCode = jn2.get("qrCode").asText();
                            invoiceEntity.dateTime = jn2.get("dateTime").asText();
                            invoiceEntity.codeDEFDGI = jn2.get("codeDEFDGI").asText();
                            invoiceEntity.counters = jn2.get("counters").asText();
                            invoiceEntity.nim = jn2.get("nim").asText();
                            invoiceEntity.status = "CONFRIM";
                        }
                        System.out.println("La reponse final: "+jn2.toString());
                    }
                    //
                    //InvoiceResponseDataDto resp = bridgeService.forwardInvoice(req);
                    return Response.ok(invoiceEntity).build();
                } else {
                    return Response.status(405).entity("Cette facture a déjà été traité.").build();
                }
            } else {

                //InvoiceEntity invoiceEntity = new InvoiceEntity();
                //invoiceEntity.status = "PENDING";
                invoice.id = null;
                invoice.isf = entreprise.isf;
                invoice.email = entreprise.email;
                invoice.nif = entreprise.nif;
                //
                //
                invoice.persist();
                System.out.println("Le isf 2:" + entreprise.isf);
                System.out.println("Le email 2:" + email);
                //La logique finale...
                //On retourne normalement : InvoiceEntity
                JsonNode code = processusDemande(invoice, entreprise.token);//
                System.out.println("Le code 2: " + code);
                //
                if (code.get("errorDesc") != null) {
                    invoice.errorCode = code.get("errorCode").asText();
                    invoice.errorDesc = code.get("errorDesc").asText();
                    invoice.status = "PROBLEM";
                } else {
                    //"total":0.0,"curTotal":0.0,"vtotal":0.0
                    invoice.uid = code.get("uid").asText();
                    invoice.total = BigDecimal.valueOf(code.get("total").asDouble());
                    invoice.curTotal = BigDecimal.valueOf(code.get("curTotal").asDouble());
                    invoice.vtotal = BigDecimal.valueOf(code.get("vtotal").asDouble());
                    //
                    JsonNode jn2 = processusFinal(invoice, entreprise.token);
                    if(jn2.get("errorDesc") != null){
                        //
                        invoice.errorCode = jn2.get("errorCode").asText();
                        invoice.errorDesc = jn2.get("errorDesc").asText();
                        invoice.status = "PROBLEM";
                        //
                    } else {
                        invoice.qrCode = jn2.get("qrCode").asText();
                        invoice.dateTime = jn2.get("dateTime").asText();
                        invoice.codeDEFDGI = jn2.get("codeDEFDGI").asText();
                        invoice.counters = jn2.get("counters").asText();
                        invoice.nim = jn2.get("nim").asText();
                        invoice.status = "CONFRIM";
                    }
                    System.out.println("La reponse final: "+jn2.toString());
                }
                //
                //InvoiceResponseDataDto resp = bridgeService.forwardInvoice(req);
                return Response.ok(invoice).build();
            }

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Bridge error", "detail", e.getMessage())).build();
        }
    }


    @GET
    @Path("{uid}")
    @RolesAllowed({"ADMIN","USER"})
    @Operation(summary = "Récupérer les détails d'une facture")
    public Response getPending(@PathParam("uid") String uid) {

        // Récupérer l'email depuis le token JWT
        String email = jwt.getClaim("email");
        //
        if(email == null || email.isEmpty()){
            return Response.status(405).entity("Aucun email trouvé").build();
        }
        //
        Entreprise entreprise = Entreprise.find("email", email).firstResult();
        //
        if(entreprise == null){
            return Response.status(405).entity("Aucun utilisateur trouvé").build();
        }
        //
        InvoiceEntity invoiceEntity = InvoiceEntity.find("email = ?1 and uid = ?2", email, uid).firstResult();

        //
        if(invoiceEntity == null){
            return Response.status(405).entity("Facture non enregistré").build();
        }

        return Response.ok(invoiceEntity).build();
    }

    public JsonNode processusDemande(InvoiceEntity invoiceEntity, String dgiToken) throws IOException, InterruptedException {
        //
        //String token = incomingJson.getString("token");
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Indispensable pour LocalDateTime
        // Optionnel : pour éviter le format [2025, 12, 25...]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String jsonPayload = mapper.writeValueAsString(invoiceEntity);
        //JsonObject payload = incomingJson.getJsonObject("payload");
        //
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://developper.dgirdc.cd/edef/api/invoice"))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer "+dgiToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload)) // implicite, mais explicite pour la clarté
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());
        JsonNode jsonN = mapper.readTree(response.body());
        return jsonN;
        //return "Rep: " + response.body() + " -- " + response.statusCode();
    }

    public JsonNode processusFinal(InvoiceEntity invoiceEntity, String dgiToken) throws IOException, InterruptedException {
        //
        //String token = incomingJson.getString("token");
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Indispensable pour LocalDateTime
        // Optionnel : pour éviter le format [2025, 12, 25...]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        //
        HashMap confirm = new HashMap();
        confirm.put("total",invoiceEntity.total);
        confirm.put("vtotal",invoiceEntity.vtotal);

        String jsonPayload = mapper.writeValueAsString(confirm);
        //JsonObject payload = incomingJson.getJsonObject("payload");
        //
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://developper.dgirdc.cd/edef/api/invoice/"+invoiceEntity.uid+"/confirm"))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer "+dgiToken)
                .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload)) // implicite, mais explicite pour la clarté
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());
        JsonNode jsonN = mapper.readTree(response.body());
        return jsonN;
        //return "Rep: " + response.body() + " -- " + response.statusCode();
    }

}
