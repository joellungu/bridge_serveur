package org.middleware.dto;

import io.smallrye.common.constraint.NotNull;

import java.util.List;

public class InvoiceRequestDataDto {

    @NotNull
    private String nif;
    private String rn;
    private String type; // ex: FV
    private String mode;
    private List<ItemDto> items;
    private ClientDto client;
    private List<PaymentDto> payment;

    // Getters et Setters pour nif
    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    // Getters et Setters pour rn
    public String getRn() {
        return rn;
    }

    public void setRn(String rn) {
        this.rn = rn;
    }

    // Getters et Setters pour type
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    // Getters et Setters pour mode
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    // Getters et Setters pour items
    public List<ItemDto> getItems() {
        return items;
    }

    public void setItems(List<ItemDto> items) {
        this.items = items;
    }

    // Getters et Setters pour client
    public ClientDto getClient() {
        return client;
    }

    public void setClient(ClientDto client) {
        this.client = client;
    }

    // Getters et Setters pour payment
    public List<PaymentDto> getPayment() {
        return payment;
    }

    public void setPayment(List<PaymentDto> payment) {
        this.payment = payment;
    }


}
