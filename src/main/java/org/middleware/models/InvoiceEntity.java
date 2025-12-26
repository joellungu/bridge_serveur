package org.middleware.models;



import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoicerntity")
@RegisterForReflection
public class InvoiceEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    //
    @Column(name = "email", length = 200)
    public String email;

    //
    @Column(name = "uid", length = 200)
    public String uid;

    // === CHAMPS OBLIGATOIRES DE BASE ===
    @Column(name = "nif", length = 13)
    public String nif;

    @Column(name = "rn")
    public String rn;

    @Column(name = "company_name", length = 255)
    public String companyName;

    @Column(name = "mode", length = 3)
    public String mode;

    @Column(name = "isf", length = 10)
    public String isf;

    @Column(name = "type", length = 2)
    public String type;

    // === COLLECTIONS PRINCIPALES ===
    @ElementCollection
    @CollectionTable(name = "invoice_items", joinColumns = @JoinColumn(name = "invoice_id"))
    public List<Item> items = new ArrayList<>();

    @Embedded
    public Client client;

    @Embedded
    public Operator operator;

    // === DATES ===
    @Column(name = "issue_date")
    public LocalDateTime issueDate;

    @Column(name = "due_date")
    public LocalDateTime dueDate;

    @Column(name = "payment_date")
    public LocalDateTime paymentDate;

    @Column(name = "validity_date")
    public LocalDateTime validityDate;

    // === MONTANTS ET DEVISES ===
    @Column(name = "currency", length = 3)
    public String currency;

    @Column(name = "subtotal", precision = 15, scale = 2)
    public BigDecimal subtotal;

    @Column(name = "total", precision = 15, scale = 2)
    public BigDecimal total;

    @Column(name = "curTotal", precision = 15, scale = 2)
    public BigDecimal curTotal;

    @Column(name = "vtotal", precision = 15, scale = 2)
    public BigDecimal vtotal;

    // ----------------------

    public String dateTime;
    public String qrCode;
    public String codeDEFDGI;
    public String counters;
    public String nim;

    @ElementCollection
    @CollectionTable(name = "invoice_payments", joinColumns = @JoinColumn(name = "invoice_id"))
    public List<Payment> payments = new ArrayList<>();

    // === CHAMPS POUR FACTURES D'AVOIR ===
    @Column(name = "reference", length = 24)
    public String reference;

    @Column(name = "reference_type")
    public String referenceType;

    @Column(name = "reference_desc")
    public String referenceDesc;

    // === COMMENTAIRES ===
    @Column(name = "cmta")
    public String cmta;

    @Column(name = "cmtb")
    public String cmtb;

    // === DEVISES ===
    @Column(name = "cur_code", length = 3)
    public String curCode;

    @Column(name = "cur_date")
    public LocalDateTime curDate;

    @Column(name = "cur_rate", precision = 10, scale = 4)
    public BigDecimal curRate;

    // === CHAMPS MÉTADONNÉES ===
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @Column(name = "status", length = 20)
    public String status = "PENDING";

    @Column(name = "dgi_response", columnDefinition = "text")
    public String dgiResponse;

    @Column(name = "dgi_token", columnDefinition = "text")
    public String dgiToken;

    public String errorCode;
    //
    @Column(name = "error_desc", columnDefinition = "text")
    public String errorDesc;


    // === CONSTRUCTEURS ===
    public InvoiceEntity() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    // === CLASSES EMBARQUÉES ===

    @Embeddable
    @RegisterForReflection
    public static class Item {

        @Column(name = "code")
        public String code;

        @Column(name = "item_type", length = 3)
        public String type;

        @Column(name = "name")
        public String name;

        @Column(name = "price", precision = 15, scale = 2)
        public BigDecimal price;

        @Column(name = "quantity", precision = 12, scale = 3)
        public BigDecimal quantity;

        @Column(name = "tax_group", length = 1)
        public String taxGroup;

        @Column(name = "tax_specific_value", length = 10)
        public String taxSpecificValue;

        @Column(name = "tax_specific_amount", precision = 15, scale = 2)
        public BigDecimal taxSpecificAmount;

        @Column(name = "original_price", precision = 15, scale = 2)
        public BigDecimal originalPrice;

        @Column(name = "price_modification")
        public String priceModification;

    }

    @Embeddable
    @RegisterForReflection
    public static class Client {

        @Column(name = "client_nif", length = 13)
        public String nif;

        @Column(name = "client_name")
        public String name;

        @Column(name = "contact")
        public String contact;

        @Column(name = "address")
        public String address;

        @Column(name = "client_type", length = 2)
        public String type;

        @Column(name = "type_desc")
        public String typeDesc;
    }

    @Embeddable
    @RegisterForReflection
    public static class Operator {

        @Column(name = "operator_id")
        public UUID id;

        @Column(name = "operator_name")
        public String name;
    }

    @Embeddable
    @RegisterForReflection
    public static class Payment {

        @Column(name = "payment_name", length = 20)
        public String name;

        @Column(name = "amount", precision = 15, scale = 2)
        public BigDecimal amount;

        @Column(name = "currency_code", length = 3)
        public String currencyCode;

        @Column(name = "currency_rate", precision = 10, scale = 4)
        public BigDecimal currencyRate;
    }


    // === MÉTHODES PANACHE (OPTIONNELLES) ===
    public static List<InvoiceEntity> findByNif(String nif) {
        return list("nif", nif);
    }

    public static List<InvoiceEntity> findByStatus(String status) {
        return list("status", status);
    }

    public static InvoiceEntity findByReference(String reference) {
        return find("reference", reference).firstResult();
    }

    public static List<InvoiceEntity> findByTypeAndDateRange(String type, LocalDateTime start, LocalDateTime end) {
        return list("type = ?1 and createdAt between ?2 and ?3", type, start, end);
    }
}
