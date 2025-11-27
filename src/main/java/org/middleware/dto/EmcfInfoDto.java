package org.middleware.dto;


import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO représentant un e-MCF enregistré
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmcfInfoDto {

    /**
     * NIM de l’e-MCF
     */
    private String nim;

    /**
     * Statut :
     * Enregistré | Actif | Désactivé
     */
    private String status;

    /**
     * Nom du point de vente
     */
    private String shopName;

    /**
     * Rue
     */
    private String address1;

    /**
     * Description ou quartier
     */
    private String address2;

    /**
     * Ville
     */
    private String address3;

    /**
     * Téléphone
     */
    private String contact1;

    /**
     * Email
     */
    private String contact2;

    /**
     * Téléphone ou email complémentaire
     */
    private String contact3;

    // ======================
    // Getters & Setters
    // ======================

    public String getNim() {
        return nim;
    }

    public void setNim(String nim) {
        this.nim = nim;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getAddress1() {
        return address1;
    }

    public void setAddress1(String address1) {
        this.address1 = address1;
    }

    public String getAddress2() {
        return address2;
    }

    public void setAddress2(String address2) {
        this.address2 = address2;
    }

    public String getAddress3() {
        return address3;
    }

    public void setAddress3(String address3) {
        this.address3 = address3;
    }

    public String getContact1() {
        return contact1;
    }

    public void setContact1(String contact1) {
        this.contact1 = contact1;
    }

    public String getContact2() {
        return contact2;
    }

    public void setContact2(String contact2) {
        this.contact2 = contact2;
    }

    public String getContact3() {
        return contact3;
    }

    public void setContact3(String contact3) {
        this.contact3 = contact3;
    }
}
