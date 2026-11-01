package com.growthprint.backend.invoice.pdf;

import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.config.CompanyProperties;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoiceCurrency;
import com.growthprint.backend.invoice.entity.InvoiceLineItem;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Test data and PDF text-extraction helpers for the invoice PDF tests. */
final class InvoicePdfFixtures {

    static final UUID CLIENT_ID = UUID.fromString("7d4c1c52-5a43-4c39-9a8e-0c3e5f1b2a10");

    private InvoicePdfFixtures() {
    }

    static Client client() {
        return new Client(CLIENT_ID, "Northwind", "Northwind Trading Ltd", "billing@northwind.test", "+44 20 7946 0000",
                "https://northwind.test", ClientEnums.ClientStatus.ACTIVE, ClientEnums.RetainerTier.PRO_GROWTH, 20, 4,
                LocalDate.of(2026, 1, 1), "Ada Lovelace", ClientEnums.BillingCurrency.EUR, null);
    }

    static Invoice invoice(UUID id, InvoiceCurrency currency, InvoiceStatus status, BigDecimal subtotal,
                           BigDecimal taxPercent, BigDecimal taxAmount, BigDecimal total) {
        Instant now = Instant.parse("2026-11-01T09:00:00Z");
        return new Invoice(id, "INV-2026-0042", CLIENT_ID, "Northwind", "billing@northwind.test",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15), subtotal, taxPercent, taxAmount, total,
                currency, status, status == InvoiceStatus.PAID ? now : null, "Thank you for your business.", now, now);
    }

    /** EUR invoice: 1,000.00 + 21% tax (210.00) = 1,210.00. */
    static Invoice standardInvoice(UUID id, InvoiceStatus status) {
        return invoice(id, InvoiceCurrency.EUR, status, new BigDecimal("1000.00"), new BigDecimal("21.00"),
                new BigDecimal("210.00"), new BigDecimal("1210.00"));
    }

    static List<InvoiceLineItem> standardItems(UUID invoiceId) {
        Instant now = Instant.parse("2026-11-01T09:00:00Z");
        return List.of(
                new InvoiceLineItem(UUID.randomUUID(), invoiceId, "Website redesign - discovery workshop", 1,
                        new BigDecimal("400.00"), new BigDecimal("400.00"), now, now),
                new InvoiceLineItem(UUID.randomUUID(), invoiceId, "Monthly maintenance retainer", 3,
                        new BigDecimal("200.00"), new BigDecimal("600.00"), now, now));
    }

    /** {@code count} items "Service line NN" with amount NN.00 each (quantity 1). */
    static List<InvoiceLineItem> numberedItems(UUID invoiceId, int count) {
        Instant now = Instant.parse("2026-11-01T09:00:00Z");
        List<InvoiceLineItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            BigDecimal price = new BigDecimal(i).setScale(2, RoundingMode.UNNECESSARY);
            items.add(new InvoiceLineItem(UUID.randomUUID(), invoiceId, String.format("Service line %02d", i), 1,
                    price, price, now, now));
        }
        return items;
    }

    static CompanyProperties fullCompany() {
        CompanyProperties company = new CompanyProperties();
        company.setName("GrowthPrint");
        company.setEmail("billing@growthprint.test");
        company.setWebsite("https://growthprint.test");
        company.setAddress("1 Test Street\\nTest City");
        company.setPhone("+1 555 0100");
        company.setTaxId("TEST-TAX-123");
        company.setRegistrationNumber("TEST-REG-456");
        company.setPaymentInstructions("Bank transfer to TEST-ACCOUNT\\nUse the invoice number as reference");
        return company;
    }

    static List<String> pageTexts(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            List<String> pages = new ArrayList<>();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                pages.add(extractor.getTextFromPage(page));
            }
            return pages;
        } finally {
            reader.close();
        }
    }

    static String allText(byte[] pdf) throws IOException {
        return String.join("\n", pageTexts(pdf));
    }

    /** Collapses all whitespace (line breaks from wrapping included) into single spaces. */
    static String normalized(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * Removes all whitespace. Text extraction does not always emit a space between two adjacent table cells
     * (e.g. "Tax (21%)" and "€210.00"), so label/value pairs are compared whitespace-insensitively.
     */
    static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    static String[] compact(String first, String... more) {
        String[] result = new String[more.length + 1];
        result[0] = compact(first);
        for (int i = 0; i < more.length; i++) {
            result[i + 1] = compact(more[i]);
        }
        return result;
    }
}
