package com.growthprint.backend.invoice.pdf;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import com.growthprint.backend.invoice.repository.InvoiceLineItemRepository;
import com.growthprint.backend.invoice.repository.InvoicePaymentReportRepository;
import com.growthprint.backend.invoice.repository.InvoiceRepository;
import com.growthprint.backend.testsupport.StandaloneMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.CLIENT_ID;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.allText;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.client;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.fullCompany;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.standardInvoice;
import static com.growthprint.backend.invoice.pdf.InvoicePdfFixtures.standardItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the PDF endpoints: real controller + real service + real renderer (repositories mocked)
 * + the real GlobalExceptionHandler. Role checks are in {@link InvoicePdfSecurityTest}.
 */
class InvoicePdfControllerTest {

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final InvoiceLineItemRepository lineItemRepository = mock(InvoiceLineItemRepository.class);
    private final InvoicePaymentReportRepository reportRepository = mock(InvoicePaymentReportRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final AuthService authService = mock(AuthService.class);

    private final MockMvc mvc = StandaloneMvc.of(new InvoicePdfController(new InvoicePdfService(invoiceRepository,
            lineItemRepository, reportRepository, clientRepository, authService,
            new InvoicePdfRenderer(fullCompany()))));

    @AfterEach
    void tearDown() {
        StandaloneMvc.clearAuthentication();
    }

    @Test
    void adminDownloadIsAPdfAttachment() throws Exception {
        UUID id = givenInvoice();

        var response = mvc.perform(get("/api/v1/invoices/{id}/pdf", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"GrowthPrint-Invoice-INV-2026-0042.pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();

        byte[] body = response.getContentAsByteArray();
        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
        assertThat(response.getContentLength()).isEqualTo(body.length);
        assertThat(allText(body)).contains("INV-2026-0042");
    }

    @Test
    void inlineDispositionIsSupportedForPreview() throws Exception {
        UUID id = givenInvoice();

        mvc.perform(get("/api/v1/invoices/{id}/pdf", id).param("disposition", "inline"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"GrowthPrint-Invoice-INV-2026-0042.pdf\""));
    }

    @Test
    void unknownDispositionIsABadRequest() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(get("/api/v1/invoices/{id}/pdf", id).param("disposition", "evil"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownInvoiceIsNotFoundAsJson() throws Exception {
        UUID id = UUID.randomUUID();
        when(invoiceRepository.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/invoices/{id}/pdf", id))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                .andExpect(jsonPath("$.message").value("Invoice not found"));
    }

    @Test
    void clientDownloadsOwnInvoice() throws Exception {
        UUID id = givenInvoice();
        UUID userId = givenClientUser(CLIENT_ID);
        StandaloneMvc.authenticateAs(userId, "CLIENT");

        mvc.perform(get("/api/v1/invoices/my/{id}/pdf", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"GrowthPrint-Invoice-INV-2026-0042.pdf\""));
    }

    @Test
    void anotherClientsInvoiceIsNotFound() throws Exception {
        UUID id = givenInvoice();
        UUID userId = givenClientUser(UUID.randomUUID());
        StandaloneMvc.authenticateAs(userId, "CLIENT");

        mvc.perform(get("/api/v1/invoices/my/{id}/pdf", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Invoice not found"));
    }

    private UUID givenInvoice() {
        UUID id = UUID.randomUUID();
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(standardInvoice(id, InvoiceStatus.ISSUED)));
        when(lineItemRepository.findByInvoiceId(id)).thenReturn(standardItems(id));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client()));
        when(reportRepository.findByInvoiceIdOrderByCreatedAtDesc(id)).thenReturn(List.of());
        return id;
    }

    private UUID givenClientUser(UUID clientId) {
        UUID userId = UUID.randomUUID();
        when(authService.getUserById(userId)).thenReturn(new User(userId, "client@example.test",
                "client@example.test", "hash", UserRole.CLIENT, AccountStatus.ACTIVE, clientId, Instant.now(),
                Instant.now()));
        return userId;
    }
}
