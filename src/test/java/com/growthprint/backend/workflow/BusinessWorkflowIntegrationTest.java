package com.growthprint.backend.workflow;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.security.JwtTokenProvider;
import com.growthprint.backend.testsupport.AbstractPostgresIntegrationTest;
import com.growthprint.backend.testsupport.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * End-to-end business workflows over HTTP against a real, freshly migrated PostgreSQL schema
 * (all Flyway migrations incl. V14/V15), with real JWTs, SecurityConfig and ownership checks.
 * <p>
 * Runs only when an isolated database is available (GP_IT_DATABASE_* env vars or Docker); otherwise skipped.
 * Each test creates its own clients/users, so tests are independent within the shared per-run schema.
 */
@PostgresIntegrationTest
class BusinessWorkflowIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private UUID clientA;
    private UUID clientB;
    private String adminToken;
    private String clientAToken;
    private String clientAEmail;
    private String clientBToken;

    @BeforeEach
    void createTenants() {
        clientA = insertClient("Client A");
        clientB = insertClient("Client B");
        adminToken = tokenFor(insertUser(UserRole.ADMIN, null));
        User userA = insertUser(UserRole.CLIENT, clientA);
        clientAEmail = userA.getEmail();
        clientAToken = tokenFor(userA);
        clientBToken = tokenFor(insertUser(UserRole.CLIENT, clientB));
    }

    // ---- Proposals (proves the JSONB mapping fix C1 on real PostgreSQL) --------------------------

    @Test
    void proposalLifecycleCreateUpdatePublishAccept() throws Exception {
        String proposalId = id(call(HttpMethod.POST, "/api/v1/proposals", adminToken, proposalJson(clientA), 200));

        call(HttpMethod.PUT, "/api/v1/proposals/" + proposalId, adminToken,
                proposalJson(clientA).replace("Platform rebuild", "Platform rebuild v2"), 200);
        call(HttpMethod.POST, "/api/v1/proposals/" + proposalId + "/publish", adminToken,
                "{\"validUntil\":\"" + LocalDate.now(ZoneOffset.UTC).plusDays(20) + "\"}", 200);

        // Another client may not decide on it (403), the owner may.
        call(HttpMethod.POST, "/api/v1/proposals/my/" + proposalId + "/decision", clientBToken,
                "{\"decision\":\"ACCEPT\"}", 403);
        MvcResult accepted = call(HttpMethod.POST, "/api/v1/proposals/my/" + proposalId + "/decision", clientAToken,
                "{\"decision\":\"ACCEPT\",\"notes\":\"Go\"}", 200);

        assertThat((String) JsonPath.read(body(accepted), "$.status")).isEqualTo("ACCEPTED");
        assertThat((String) JsonPath.read(body(accepted), "$.title")).isEqualTo("Platform rebuild v2");
        assertThat((String) JsonPath.read(body(accepted), "$.acceptedBy")).isEqualTo(clientAEmail);
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(deliverables) FROM proposals WHERE id = ?::uuid",
                String.class, proposalId)).isEqualTo("array");

        // Client lists only own proposals.
        assertThat(body(call(HttpMethod.GET, "/api/v1/proposals/my", clientBToken, null, 200)))
                .doesNotContain(proposalId);
    }

    @Test
    void decidingOnAnExpiredProposalPersistsExpired() throws Exception {
        String proposalId = id(call(HttpMethod.POST, "/api/v1/proposals", adminToken, proposalJson(clientA), 200));
        jdbc.update("UPDATE proposals SET status = 'SENT_FOR_REVIEW', valid_until = (now() AT TIME ZONE 'UTC')::date - 1 WHERE id = ?::uuid",
                proposalId);

        call(HttpMethod.POST, "/api/v1/proposals/my/" + proposalId + "/decision", clientAToken,
                "{\"decision\":\"ACCEPT\"}", 409);

        // Previously the EXPIRED update was rolled back together with the exception.
        assertThat(jdbc.queryForObject("SELECT status FROM proposals WHERE id = ?::uuid", String.class, proposalId))
                .isEqualTo("EXPIRED");
    }

    @Test
    void unknownProposalIs404() throws Exception {
        call(HttpMethod.GET, "/api/v1/proposals/" + UUID.randomUUID(), adminToken, null, 404);
    }

    // ---- Support request -> classify -> estimate -> client approval -----------------------------

    @Test
    void supportRequestEstimateApprovalWorkflow() throws Exception {
        MvcResult created = call(HttpMethod.POST, "/api/v1/support-requests/my", clientAToken,
                "{\"title\":\"Checkout broken\",\"category\":\"PAID_AD_HOC_SUPPORT\",\"description\":\"Fails\","
                        + "\"businessImpact\":\"CRITICAL_BLOCKER\",\"urgency\":\"URGENT\"}", 200);
        String requestId = id(created);
        assertThat((String) JsonPath.read(body(created), "$.reference")).matches("GP-REQ-\\d{4}-[0-9A-F]{8}");

        call(HttpMethod.POST, "/api/v1/support-requests/" + requestId + "/classify", adminToken,
                "{\"category\":\"PAID_AD_HOC_SUPPORT\",\"adminNotes\":\"Paid work\"}", 200);
        call(HttpMethod.POST, "/api/v1/support-requests/" + requestId + "/estimate", adminToken,
                "{\"estimatedHours\":3,\"hourlyRate\":100,\"deliveryTimelineDays\":2,\"explanation\":\"Fix\"}", 200);

        // Decision 16: re-classifying replaces the estimate (never a second one) and resets the request.
        MvcResult reclassified = call(HttpMethod.POST, "/api/v1/support-requests/" + requestId + "/classify",
                adminToken, "{\"category\":\"PAID_AD_HOC_SUPPORT\"}", 200);
        assertThat((String) JsonPath.read(body(reclassified), "$.status")).isEqualTo("ESTIMATE_PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_estimates WHERE request_id = ?::uuid",
                Integer.class, requestId)).isZero();
        call(HttpMethod.POST, "/api/v1/support-requests/" + requestId + "/estimate", adminToken,
                "{\"estimatedHours\":4,\"hourlyRate\":100,\"deliveryTimelineDays\":2,\"explanation\":\"Fix v2\"}", 200);

        // Another client cannot approve it; the owner can, and the audit trail records the real user.
        call(HttpMethod.POST, "/api/v1/support-requests/my/" + requestId + "/approve-estimate", clientBToken,
                "{\"approvedBy\":\"spoof\"}", 404);
        MvcResult approved = call(HttpMethod.POST, "/api/v1/support-requests/my/" + requestId + "/approve-estimate",
                clientAToken, "{\"approvedBy\":\"Somebody Else\"}", 200);

        assertThat((String) JsonPath.read(body(approved), "$.status")).isEqualTo("READY_TO_START");
        assertThat((String) JsonPath.read(body(approved), "$.estimate.acceptedBy")).isEqualTo(clientAEmail);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_estimates WHERE request_id = ?::uuid",
                Integer.class, requestId)).isEqualTo(1);

        // Lists still render (batch mapping) and are scoped per client.
        assertThat(body(call(HttpMethod.GET, "/api/v1/support-requests", adminToken, null, 200))).contains(requestId);
        assertThat(body(call(HttpMethod.GET, "/api/v1/support-requests/my", clientBToken, null, 200)))
                .doesNotContain(requestId);
    }

    // ---- Invoice -> client payment report -> admin verification -> PAID --------------------------

    @Test
    void clientReportsPaymentAndOnlyAdminVerificationMarksInvoicePaid() throws Exception {
        String invoiceId = id(call(HttpMethod.POST, "/api/v1/invoices", adminToken, invoiceJson(clientA, "ISSUED"), 200));

        String report = "{\"amount\":121.00,\"paymentMethod\":\"BANK_TRANSFER\",\"reference\":\"ACH-1\","
                + "\"paidOn\":\"" + LocalDate.now(ZoneOffset.UTC) + "\"}";

        // A client can never use the admin reconcile endpoint.
        call(HttpMethod.POST, "/api/v1/invoices/" + invoiceId + "/reconcile", clientAToken, "{\"status\":\"PAID\"}", 403);
        // Another client's invoice is not found.
        call(HttpMethod.POST, "/api/v1/invoices/my/" + invoiceId + "/reconcile", clientBToken, report, 404);

        MvcResult reported = call(HttpMethod.POST, "/api/v1/invoices/my/" + invoiceId + "/reconcile", clientAToken,
                report, 201);
        String reportId = id(reported);
        assertThat((String) JsonPath.read(body(reported), "$.status")).isEqualTo("PENDING");
        assertThat(invoiceStatus(invoiceId)).isEqualTo("ISSUED");

        // Second pending report is a conflict.
        call(HttpMethod.POST, "/api/v1/invoices/my/" + invoiceId + "/reconcile", clientAToken, report, 409);
        // Clients cannot verify.
        call(HttpMethod.POST, "/api/v1/invoices/payment-reports/" + reportId + "/verify", clientAToken, "{}", 403);

        MvcResult verified = call(HttpMethod.POST, "/api/v1/invoices/payment-reports/" + reportId + "/verify",
                adminToken, "{\"note\":\"Matched statement\"}", 200);
        assertThat((String) JsonPath.read(body(verified), "$.invoiceStatus")).isEqualTo("PAID");
        assertThat(invoiceStatus(invoiceId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT paid_at IS NOT NULL FROM invoices WHERE id = ?::uuid",
                Boolean.class, invoiceId)).isTrue();

        // Reviewed reports cannot be reviewed again; the client sees its history.
        call(HttpMethod.POST, "/api/v1/invoices/payment-reports/" + reportId + "/reject", adminToken,
                "{\"note\":\"late\"}", 409);
        assertThat(body(call(HttpMethod.GET, "/api/v1/invoices/my/" + invoiceId + "/payment-reports", clientAToken,
                null, 200))).contains("VERIFIED");
    }

    @Test
    void invoicesCannotBeCreatedAsPaid() throws Exception {
        call(HttpMethod.POST, "/api/v1/invoices", adminToken, invoiceJson(clientA, "PAID"), 400);
    }

    @Test
    void duplicateCrmLeadIsA409NotA500() throws Exception {
        String lead = "{\"companyName\":\"Dup Co " + UUID.randomUUID() + "\",\"website\":\"https://dup.test\","
                + "\"industry\":\"Retail\",\"location\":\"NYC\",\"contactName\":\"Dee\",\"contactEmail\":\"dee@dup.test\","
                + "\"source\":\"REFERRAL\",\"targetService\":\"SOFTWARE_DEV\",\"identifiedProblem\":\"p\","
                + "\"researchSummary\":\"r\",\"qualificationScore\":50,\"pipelineStage\":\"DISCOVERED\","
                + "\"nextAction\":\"call\",\"doNotContact\":false}";
        call(HttpMethod.POST, "/api/v1/crm-leads", adminToken, lead, 200);
        call(HttpMethod.POST, "/api/v1/crm-leads", adminToken, lead, 409);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private MvcResult call(HttpMethod method, String path, String token, String json, int expectedStatus)
            throws Exception {
        var builder = request(method, path).header("Authorization", "Bearer " + token);
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        MvcResult result = mvc.perform(builder).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("%s %s -> %s", method, path, result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return result;
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static String id(MvcResult result) throws Exception {
        return JsonPath.read(body(result), "$.id").toString();
    }

    private String invoiceStatus(String invoiceId) {
        return jdbc.queryForObject("SELECT status FROM invoices WHERE id = ?::uuid", String.class, invoiceId);
    }

    private UUID insertClient(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clients (id, name, legal_name, contact_email, website, status, contract_start_date, "
                        + "account_manager, billing_currency) VALUES (?, ?, ?, ?, 'https://example.test', 'ACTIVE', "
                        + "CURRENT_DATE, 'IT', 'USD')",
                id, name, name + " LLC", "ops+" + id + "@example.test");
        return id;
    }

    private User insertUser(UserRole role, UUID clientId) {
        UUID id = UUID.randomUUID();
        String email = role.name().toLowerCase() + "+" + id + "@example.test";
        jdbc.update("INSERT INTO users (id, email, normalized_email, password_hash, role, account_status, client_id, "
                        + "client_role) VALUES (?, ?, ?, 'not-a-real-hash', ?, 'ACTIVE', ?, ?)",
                id, email, email, role.name(), clientId, role == UserRole.CLIENT ? "CLIENT_OWNER" : null);
        return new User(id, email, email, "x", role, AccountStatus.ACTIVE, clientId, Instant.now(), Instant.now());
    }

    private String tokenFor(User user) {
        return tokenProvider.generateToken(user);
    }

    private static String proposalJson(UUID clientId) {
        return "{\"clientId\":\"" + clientId + "\",\"title\":\"Platform rebuild\",\"serviceCategory\":\"SOFTWARE_DEV\","
                + "\"scopeSummary\":\"Rebuild the customer platform\",\"deliverables\":[\"Web app\",\"API\"],"
                + "\"exclusions\":[\"Hosting\"],\"totalPrice\":1000.00,\"currency\":\"USD\","
                + "\"paymentMilestones\":[{\"description\":\"Deposit\",\"percentage\":50,\"amount\":500.00},"
                + "{\"description\":\"Delivery\",\"percentage\":50,\"amount\":500.00}],"
                + "\"timelineDuration\":\"6 weeks\",\"validUntil\":\"" + LocalDate.now(ZoneOffset.UTC).plusDays(30)
                + "\",\"notes\":null}";
    }

    private static String invoiceJson(UUID clientId, String status) {
        return "{\"invoiceNumber\":\"INV-IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "\","
                + "\"clientId\":\"" + clientId + "\",\"clientName\":\"Client\",\"clientEmail\":\"ap@example.test\","
                + "\"issueDate\":\"" + LocalDate.now(ZoneOffset.UTC) + "\",\"dueDate\":\""
                + LocalDate.now(ZoneOffset.UTC).plusDays(14) + "\",\"subtotal\":100.00,\"taxPercent\":21,"
                + "\"taxAmount\":21.00,\"totalAmount\":121.00,\"currency\":\"USD\",\"status\":\"" + status + "\","
                + "\"lineItems\":[{\"description\":\"Work\",\"quantity\":2,\"unitPrice\":50.00,\"amount\":100.00}]}";
    }
}
