package org.middleware.service;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SchemaMigrationService {

    private static final Logger LOG = Logger.getLogger(SchemaMigrationService.class.getName());

    @Inject
    EntityManager entityManager;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        widenInvoiceCodeColumns();
    }

    private void widenInvoiceCodeColumns() {
        executeAlter("ALTER TABLE IF EXISTS invoicerntity ALTER COLUMN mode TYPE varchar(20)");
        executeAlter("ALTER TABLE IF EXISTS invoicerntity ALTER COLUMN currency TYPE varchar(20)");
        executeAlter("ALTER TABLE IF EXISTS invoicerntity ALTER COLUMN cur_code TYPE varchar(20)");
        executeAlter("ALTER TABLE IF EXISTS invoice_items ALTER COLUMN item_type TYPE varchar(20)");
        executeAlter("ALTER TABLE IF EXISTS invoice_payments ALTER COLUMN currency_code TYPE varchar(20)");
    }

    private void executeAlter(String sql) {
        try {
            entityManager.createNativeQuery(sql).executeUpdate();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Migration SQL ignoree: " + sql + " - " + e.getMessage(), e);
        }
    }
}
