package com.growthprint.backend.invoice.pdf;

import com.growthprint.backend.config.CompanyProperties;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoiceCurrency;
import com.growthprint.backend.invoice.entity.InvoiceLineItem;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.allText;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.client;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.compact;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.fullCompany;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.normalized;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.numberedItems;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.pageTexts;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.standardInvoice;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.standardItems;
import static org.assertj.core.api.Assertions.assertThat;

/** Renders real PDFs and asserts on the text extracted back out of the bytes with OpenPDF. */
class InvoicePdfRendererTest {

    /** Set (e.g. -Dinvoice.pdf.sample.dir=...) to keep a sample PDF for manual inspection. */
    private static final String SAMPLE_DIR_PROPERTY = "invoice.pdf.sample.dir";

    @Test
    void rendersInvoiceNumberClientLineItemsTotalsAndBalance() throws IOException {
        UUID id = UUID.randomUUID();
        Invoice invoice = standardInvoice(id, InvoiceStatus.ISSUED);
        byte[] pdf = new InvoicePdfRenderer(fullCompany())
                .render(new InvoicePdfData(invoice, standardItems(id), client(), new BigDecimal("500.00")));

        saveSampleIfRequested(pdf, "GrowthPrint-Invoice-INV-2026-0042.pdf");

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = normalized(allText(pdf));
        assertThat(text)
                .contains("INVOICE", "INV-2026-0042", "ISSUED")
                .contains("Northwind", "Northwind Trading Ltd", "billing@northwind.test")
                .contains("1 Nov 2026", "15 Nov 2026")
                .contains("Website redesign - discovery workshop", "Monthly maintenance retainer")
                .contains("€400.00", "€200.00", "€600.00")
                .contains("All amounts in EUR")
                .contains("Thank you for your business.");
        // Configured seller details and payment instructions
        assertThat(text)
                .contains("GrowthPrint", "billing@growthprint.test", "https://growthprint.test", "+1 555 0100")
                .contains("1 Test Street", "Test City", "TEST-TAX-123", "TEST-REG-456")
                .contains("PAYMENT INSTRUCTIONS", "Bank transfer to TEST-ACCOUNT",
                        "Use the invoice number as reference");
        assertThat(compact(text)).contains(compact("Issue date 1 Nov 2026", "Due date 15 Nov 2026",
                "Subtotal €1,000.00", "Tax (21%) €210.00", "Total €1,210.00", "Amount paid €500.00",
                "Balance due €710.00"));
        assertThat(text).contains("Page 1 of 1").doesNotContain("null");
    }

    @Test
    void multiPageInvoiceRepeatsTableHeaderAndNumbersEveryPage() throws IOException {
        UUID id = UUID.randomUUID();
        List<InvoiceLineItem> items = numberedItems(id, 60);
        // 1 + 2 + ... + 60 = 1,830.00; 10% tax = 183.00; total 2,013.00
        Invoice invoice = InvoicePdfFixtures.invoice(id, InvoiceCurrency.USD, InvoiceStatus.ISSUED,
                new BigDecimal("1830.00"), new BigDecimal("10.00"), new BigDecimal("183.00"),
                new BigDecimal("2013.00"));

        byte[] pdf = new InvoicePdfRenderer(fullCompany())
                .render(new InvoicePdfData(invoice, items, client(), BigDecimal.ZERO));

        saveSampleIfRequested(pdf, "GrowthPrint-Invoice-60-lines.pdf");

        List<String> pages = pageTexts(pdf);
        assertThat(pages).hasSizeGreaterThan(1);
        int total = pages.size();
        for (int page = 1; page <= total; page++) {
            String text = normalized(pages.get(page - 1));
            if (text.contains("Service line")) {
                assertThat(text).as("page %d with line items repeats the table header", page)
                        .contains("Description", "Qty", "Unit price", "Amount");
            }
            assertThat(text).as("page %d footer", page).contains("Page " + page + " of " + total);
            assertThat(text).as("page %d footer names the invoice", page).contains("Invoice INV-2026-0042");
        }
        String all = normalized(String.join("\n", pages));
        for (int i = 1; i <= 60; i++) {
            assertThat(all).contains(String.format("Service line %02d", i));
        }
        assertThat(all.indexOf("Subtotal")).as("totals follow the last line item")
                .isGreaterThan(all.indexOf("Service line 60"));
        assertThat(compact(all)).contains(compact("Subtotal $1,830.00", "Tax (10%) $183.00", "Total $2,013.00",
                "Amount paid $0.00", "Balance due $2,013.00"));
    }

    @Test
    void omitsCompanyBlocksThatAreNotConfigured() throws IOException {
        UUID id = UUID.randomUUID();
        byte[] pdf = new InvoicePdfRenderer(new CompanyProperties())
                .render(new InvoicePdfData(standardInvoice(id, InvoiceStatus.ISSUED), standardItems(id), client(),
                        BigDecimal.ZERO));

        String text = normalized(allText(pdf));
        assertThat(text).contains("INVOICE", "INV-2026-0042", "Northwind");
        assertThat(compact(text)).contains(compact("Total €1,210.00", "Balance due €1,210.00"));
        assertThat(text).doesNotContain("PAYMENT INSTRUCTIONS", "Tax ID", "Registration", "null");
    }

    @Test
    void longDescriptionsWrapInsideTheirCell() throws IOException {
        UUID id = UUID.randomUUID();
        String longDescription = "Comprehensive platform migration including data model review, "
                + "API hardening, accessibility remediation, performance profiling, observability dashboards, "
                + "security review of authentication flows and a handover workshop for the internal team "
                + "ending with zebracrossing";
        Instant now = Instant.parse("2026-11-01T09:00:00Z");
        List<InvoiceLineItem> items = List.of(new InvoiceLineItem(UUID.randomUUID(), id, longDescription, 1,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), now, now));

        byte[] pdf = new InvoicePdfRenderer(fullCompany())
                .render(new InvoicePdfData(standardInvoice(id, InvoiceStatus.ISSUED), items, client(), null));

        String raw = allText(pdf);
        assertThat(normalized(raw)).contains(normalized(longDescription));
        assertThat(raw).as("description must wrap over several lines").doesNotContain(longDescription);
    }

    @Test
    void paidInvoiceHasNoBalanceDue() throws IOException {
        UUID id = UUID.randomUUID();
        byte[] pdf = new InvoicePdfRenderer(fullCompany())
                .render(new InvoicePdfData(standardInvoice(id, InvoiceStatus.PAID), standardItems(id), client(),
                        BigDecimal.ZERO));

        String text = normalized(allText(pdf));
        assertThat(text).contains("PAID", "Paid on");
        assertThat(compact(text)).contains(compact("Amount paid €1,210.00", "Balance due €0.00"));
    }

    @Test
    void rendersWithoutClientRecordUsingInvoiceSnapshot() throws IOException {
        UUID id = UUID.randomUUID();
        byte[] pdf = new InvoicePdfRenderer(fullCompany())
                .render(new InvoicePdfData(standardInvoice(id, InvoiceStatus.ISSUED), standardItems(id), null,
                        BigDecimal.ZERO));

        assertThat(normalized(allText(pdf))).contains("Northwind", "billing@northwind.test").doesNotContain("null");
    }

    @Test
    void producesAValidPdfDocument() throws IOException {
        UUID id = UUID.randomUUID();
        byte[] pdf = new InvoicePdfRenderer(fullCompany())
                .render(new InvoicePdfData(standardInvoice(id, InvoiceStatus.DRAFT), standardItems(id), client(),
                        BigDecimal.ZERO));
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(reader.getInfo().get("Title")).isEqualTo("Invoice INV-2026-0042");
        } finally {
            reader.close();
        }
    }

    @Test
    void formatsMoneyPerCurrency() {
        BigDecimal amount = new BigDecimal("1234.5");
        assertThat(InvoicePdfRenderer.formatMoney(amount, InvoiceCurrency.USD)).isEqualTo("$1,234.50");
        assertThat(InvoicePdfRenderer.formatMoney(amount, InvoiceCurrency.EUR)).isEqualTo("€1,234.50");
        assertThat(InvoicePdfRenderer.formatMoney(amount, InvoiceCurrency.GBP)).isEqualTo("£1,234.50");
        assertThat(InvoicePdfRenderer.formatMoney(amount, InvoiceCurrency.CAD)).isEqualTo("CA$1,234.50");
        assertThat(InvoicePdfRenderer.formatMoney(amount, InvoiceCurrency.AUD)).isEqualTo("A$1,234.50");
        assertThat(InvoicePdfRenderer.formatMoney(new BigDecimal("-5"), InvoiceCurrency.USD)).isEqualTo("-$5.00");
    }

    private static void saveSampleIfRequested(byte[] pdf, String fileName) throws IOException {
        String dir = System.getProperty(SAMPLE_DIR_PROPERTY);
        if (dir != null && !dir.isBlank()) {
            Path target = Path.of(dir).resolve(fileName);
            Files.createDirectories(target.getParent());
            Files.write(target, pdf);
        }
    }
}
