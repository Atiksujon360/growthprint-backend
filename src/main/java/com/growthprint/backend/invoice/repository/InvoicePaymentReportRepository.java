package com.growthprint.backend.invoice.repository;

import com.growthprint.backend.invoice.entity.InvoicePaymentReport;
import com.growthprint.backend.invoice.entity.PaymentReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoicePaymentReportRepository extends JpaRepository<InvoicePaymentReport, UUID> {

    List<InvoicePaymentReport> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    boolean existsByInvoiceIdAndStatus(UUID invoiceId, PaymentReportStatus status);
}
