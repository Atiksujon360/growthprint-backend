package com.growthprint.backend.regression;

import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.client.controller.ClientController;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.client.service.ClientService;
import com.growthprint.backend.invoice.controller.InvoiceController;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.repository.InvoiceLineItemRepository;
import com.growthprint.backend.invoice.repository.InvoiceRepository;
import com.growthprint.backend.invoice.service.InvoiceService;
import com.growthprint.backend.outreach.entity.OutreachDraft;
import com.growthprint.backend.outreach.entity.OutreachDraftStatus;
import com.growthprint.backend.outreach.repository.OutreachDraftRepository;
import com.growthprint.backend.outreach.service.OutreachDraftService;
import com.growthprint.backend.project.dto.CreateProjectCommand;
import com.growthprint.backend.project.entity.ProjectStatus;
import com.growthprint.backend.project.entity.ServiceCategory;
import com.growthprint.backend.testsupport.StandaloneMvc;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for confirmed defects in invoices, clients, outreach, DTO limits and error mapping
 * (audit 2026-11-01).
 */
class CrossModuleDefectRegressionTest {

    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final InvoiceLineItemRepository lineItemRepository = mock(InvoiceLineItemRepository.class);

    private final UUID clientId = UUID.randomUUID();
    private Client client;

    @BeforeEach
    void setUp() {
        client = new Client(clientId, "Acme", "Acme LLC", "ops@acme.test", "+1 000", "https://acme.test",
                ClientEnums.ClientStatus.ACTIVE, null, null, null, LocalDate.now(), "AM",
                ClientEnums.BillingCurrency.USD, null);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---- Invoices: creation must not accept terminal statuses ---------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"PAID", "VOID", "OVERDUE"})
    void invoiceCannotBeCreatedDirectlyInATerminalOrDerivedStatus(String status) throws Exception {
        MockMvc mvc = StandaloneMvc.of(new InvoiceController(invoiceService()));

        mvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content(invoiceJson(status)))
                .andExpect(status().isBadRequest());

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void invoiceCanStillBeCreatedAsIssued() throws Exception {
        MockMvc mvc = StandaloneMvc.of(new InvoiceController(invoiceService()));

        mvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content(invoiceJson("ISSUED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"));
    }

    // ---- Clients: PATCH contract ----------------------------------------------------------------

    @Test
    void patchingClientPhoneWithTheFrontendKeyIsApplied() throws Exception {
        MockMvc mvc = StandaloneMvc.of(new ClientController(new ClientService(clientRepository)));

        mvc.perform(patch("/api/v1/clients/{id}", clientId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+1 555 0100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+1 555 0100"));
    }

    @Test
    void patchingClientWithAWrongValueTypeIsA400NotA500() throws Exception {
        MockMvc mvc = StandaloneMvc.of(new ClientController(new ClientService(clientRepository)));

        mvc.perform(patch("/api/v1/clients/{id}", clientId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":{\"nested\":true}}"))
                .andExpect(status().isBadRequest());
        verify(clientRepository, never()).save(any());
    }

    @Test
    void patchingClientWithAnInvalidEmailIsRejected() throws Exception {
        MockMvc mvc = StandaloneMvc.of(new ClientController(new ClientService(clientRepository)));

        mvc.perform(patch("/api/v1/clients/{id}", clientId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
        verify(clientRepository, never()).save(any());
    }

    // ---- Outreach: approving twice must not rewrite the approval timestamp -----------------------

    @Test
    void approvingAnAlreadyApprovedDraftIsIdempotent() {
        OutreachDraftRepository repository = mock(OutreachDraftRepository.class);
        Instant originalApproval = Instant.now().minus(3, ChronoUnit.DAYS);
        OutreachDraft draft = new OutreachDraft(UUID.randomUUID(), UUID.randomUUID(), "Acme", "Jo", "jo@acme.test",
                "Hello", "Body", null, ServiceCategory.SOFTWARE_DEV, "[]", OutreachDraftStatus.APPROVED, true,
                originalApproval, null, Instant.now(), Instant.now());
        when(repository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(repository.save(any(OutreachDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new OutreachDraftService(repository).approve(draft.getId());

        assertThat(draft.getApprovedAt()).isEqualTo(originalApproval);
    }

    // ---- DTO limits that match database columns --------------------------------------------------

    @Test
    void projectCommandRejectsValuesTheDatabaseWouldReject() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        CreateProjectCommand command = new CreateProjectCommand("GP-PRJ-" + "1".repeat(60), "n".repeat(201),
                clientId.toString(), ServiceCategory.SOFTWARE_DEV, "desc", ProjectStatus.PLANNED, 150,
                LocalDate.now(), LocalDate.now().plusDays(10), new BigDecimal("-1"), null, null, List.of());

        List<String> invalid = validator.validate(command).stream()
                .map(violation -> violation.getPropertyPath().toString()).toList();

        assertThat(invalid).contains("reference", "name", "progressPercent", "budgetTotal");
    }

    // ---- Error mapping: unique-constraint conflicts are 409, not 500 ----------------------------

    @Test
    void dataIntegrityViolationIs409WithoutLeakingSql() throws Exception {
        MockMvc mvc = StandaloneMvc.of(new ThrowingController());

        String body = mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("uk_crm_lead_company_email").doesNotContain("INSERT");
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/conflict")
        String conflict() {
            throw new DataIntegrityViolationException(
                    "could not execute statement [INSERT ...] duplicate key violates uk_crm_lead_company_email");
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private InvoiceService invoiceService() {
        return new InvoiceService(invoiceRepository, lineItemRepository, clientRepository, mock(AuthService.class));
    }

    private String invoiceJson(String status) {
        return """
                {"invoiceNumber":"INV-2026-0100","clientId":"%s","clientName":"Acme","clientEmail":"ap@acme.test",
                 "issueDate":"2026-11-01","dueDate":"2026-11-15","subtotal":100.00,"taxPercent":0,"taxAmount":0.00,
                 "totalAmount":100.00,"currency":"USD","status":"%s","notes":null,
                 "lineItems":[{"description":"Work","quantity":1,"unitPrice":100.00,"amount":100.00}]}
                """.formatted(clientId, status);
    }
}
