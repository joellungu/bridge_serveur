package org.middleware.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de réponse pour /api/info/status (DGI)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InfoResponseDto {

    /**
     * true si l'API est opérationnelle, false sinon
     */
    private Boolean status;

    /**
     * Version de l'API
     */
    private String version;

    /**
     * NIF du contribuable
     */
    private String nif;

    /**
     * NIM de l’e-MCF dont le jeton a été utilisé
     */
    private String nim;

    /**
     * Date de validité de la clé API utilisée
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime tokenValid;

    /**
     * Date et heure du serveur API
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime serverDateTime;

    /**
     * Liste des e-MCF
     */
    private List<EmcfInfoDto> emcfList;

    // ======================
    // Getters & Setters
    // ======================

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    public String getNim() {
        return nim;
    }

    public void setNim(String nim) {
        this.nim = nim;
    }

    public LocalDateTime getTokenValid() {
        return tokenValid;
    }

    public void setTokenValid(LocalDateTime tokenValid) {
        this.tokenValid = tokenValid;
    }

    public LocalDateTime getServerDateTime() {
        return serverDateTime;
    }

    public void setServerDateTime(LocalDateTime serverDateTime) {
        this.serverDateTime = serverDateTime;
    }

    public List<EmcfInfoDto> getEmcfList() {
        return emcfList;
    }

    public void setEmcfList(List<EmcfInfoDto> emcfList) {
        this.emcfList = emcfList;
    }
}

