package com.growthprint.backend.invoice.repository;

import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByClientId(UUID clientId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByStatus(InvoiceStatus status);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
