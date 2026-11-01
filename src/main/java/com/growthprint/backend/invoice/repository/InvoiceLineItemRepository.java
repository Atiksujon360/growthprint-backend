package com.growthprint.backend.invoice.repository;

import com.growthprint.backend.invoice.entity.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, UUID> {

    List<InvoiceLineItem> findByInvoiceId(UUID invoiceId);

    /** Batch read for invoice lists (one query instead of one per invoice). */
    List<InvoiceLineItem> findByInvoiceIdIn(java.util.Collection<UUID> invoiceIds);
}
