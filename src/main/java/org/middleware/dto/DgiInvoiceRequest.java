package org.middleware.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.middleware.models.InvoiceEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DgiInvoiceRequest {
    public String nif;
    public String rn;
    public String type;
    public String mode;
    public String isf;
    public String currency;
    public LocalDateTime issueDate;
    public LocalDateTime paymentDate;
    public LocalDateTime validityDate;
    public String cmta;
    public String cmtb;
    public String cmtc;
    public String cmtd;
    public String cmte;
    public String cmtf;
    public String cmtg;
    public String cmth;
    public Client client;
    public Operator operator;
    public List<Item> items;
    public List<Payment> payment;
    public String reference;
    public String referenceType;
    public String referenceDesc;
    public String curCode;
    public LocalDateTime curDate;
    public BigDecimal curRate;

    public static DgiInvoiceRequest from(InvoiceEntity invoice) {
        DgiInvoiceRequest request = new DgiInvoiceRequest();
        request.nif = invoice.nif;
        request.rn = invoice.rn;
        request.type = invoice.type;
        request.mode = invoice.mode;
        request.isf = invoice.isf;
        request.currency = invoice.currency;
        request.issueDate = invoice.issueDate;
        request.paymentDate = invoice.paymentDate;
        request.validityDate = invoice.validityDate;
        request.cmta = invoice.cmta;
        request.cmtb = invoice.cmtb;
        request.cmtc = invoice.cmtc;
        request.cmtd = invoice.cmtd;
        request.cmte = invoice.cmte;
        request.cmtf = invoice.cmtf;
        request.cmtg = invoice.cmtg;
        request.cmth = invoice.cmth;
        request.reference = invoice.reference;
        request.referenceType = invoice.referenceType;
        request.referenceDesc = invoice.referenceDesc;
        request.curCode = invoice.curCode;
        request.curDate = invoice.curDate;
        request.curRate = invoice.curRate;

        if (invoice.client != null) {
            request.client = new Client();
            request.client.nif = invoice.client.nif;
            request.client.name = invoice.client.name;
            request.client.contact = invoice.client.contact;
            request.client.address = invoice.client.address;
            request.client.type = invoice.client.type;
            request.client.typeDesc = invoice.client.typeDesc;
        }

        if (invoice.operator != null) {
            request.operator = new Operator();
            request.operator.id = invoice.operator.id != null ? invoice.operator.id.toString() : null;
            request.operator.name = invoice.operator.name;
        }

        request.items = new ArrayList<>();
        if (invoice.items != null) {
            for (InvoiceEntity.Item source : invoice.items) {
                Item item = new Item();
                item.code = source.code;
                item.type = source.type;
                item.name = source.name;
                item.price = source.price;
                item.quantity = source.quantity;
                item.unit = source.unit;
                item.taxGroup = source.taxGroup;
                item.taxSpecificValue = source.taxSpecificValue;
                item.taxSpecificAmount = source.taxSpecificAmount;
                item.originalPrice = source.originalPrice;
                item.priceModification = source.priceModification;
                request.items.add(item);
            }
        }

        request.payment = new ArrayList<>();
        if (invoice.payments != null) {
            for (InvoiceEntity.Payment source : invoice.payments) {
                Payment payment = new Payment();
                payment.name = source.name;
                payment.amount = source.amount;
                payment.currencyCode = source.currencyCode;
                payment.currencyRate = source.currencyRate;
                request.payment.add(payment);
            }
        }

        if (request.payment.isEmpty()) {
            Payment payment = new Payment();
            payment.name = "ESPECES";
            payment.amount = invoice.total;
            if (invoice.currency != null && !"CDF".equalsIgnoreCase(invoice.currency)) {
                payment.currencyCode = invoice.currency;
                payment.currencyRate = invoice.curRate != null ? invoice.curRate : BigDecimal.ONE;
            }
            request.payment.add(payment);
        }

        return request;
    }

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Client {
        public String nif;
        public String name;
        public String contact;
        public String address;
        public String type;
        public String typeDesc;
    }

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Operator {
        public String id;
        public String name;
    }

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        public String code;
        public String type;
        public String name;
        public BigDecimal price;
        public BigDecimal quantity;
        public String unit;
        public String taxGroup;
        public String taxSpecificValue;
        public BigDecimal taxSpecificAmount;
        public BigDecimal originalPrice;
        public String priceModification;
    }

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payment {
        public String name;
        public BigDecimal amount;
        public String currencyCode;
        public BigDecimal currencyRate;
    }
}
