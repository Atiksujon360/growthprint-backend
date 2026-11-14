package com.growthprint.backend.workflow;

import com.growthprint.backend.testsupport.AbstractPostgresIntegrationTest;
import com.growthprint.backend.testsupport.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * The commercial onboarding chain over HTTP against real PostgreSQL:
 * public inquiry -> CRM lead -> client -> portal invitation -> accepted account -> login -> own data only,
 * plus account suspension, re-activation and password change.
 */
@PostgresIntegrationTest
class PortalOnboardingWorkflowIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String ADMIN_PASSWORD = "Admin-Password-123";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private String adminToken;

    @BeforeEach
    void createAdmin() throws Exception {
        adminEmail = "admin+" + UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO users (id, email, normalized_email, password_hash, role, account_status, full_name) "
                        + "VALUES (?, ?, ?, ?, 'ADMIN', 'ACTIVE', 'Ada Admin')",
                UUID.randomUUID(), adminEmail, adminEmail, passwordEncoder.encode(ADMIN_PASSWORD));
        adminToken = login(adminEmail, ADMIN_PASSWORD, 200);
    }

    @Test
    void inquiryBecomesLeadThenClientWithPortalAccess() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String contactEmail = "owner+" + unique + "@acme.test";

        // 1. Public inquiry (no token).
        call(HttpMethod.POST, "/api/v1/public/inquiries", null, "{\"name\":\"Olive Owner\",\"businessEmail\":\""
                + contactEmail + "\",\"company\":\"Acme " + unique + "\",\"website\":\"https://acme.test\","
                + "\"serviceCategory\":\"SOFTWARE_DEV\",\"projectType\":\"Web app\",\"projectDescription\":"
                + "\"We need a new customer portal built.\",\"preferredContactMethod\":\"EMAIL\",\"consent\":true}", 201);
        List<String> inquiryIds = JsonPath.read(body(call(HttpMethod.GET, "/api/v1/leads", adminToken, null, 200)),
                "$[?(@.businessEmail == '" + contactEmail + "')].id");
        assertThat(inquiryIds).hasSize(1);

        // 2. Inquiry -> CRM lead.
        String leadId = id(call(HttpMethod.POST, "/api/v1/crm-leads/inquiry/" + inquiryIds.get(0) + "/convert",
                adminToken, crmLeadJson("Acme " + unique, contactEmail), 200));

        // 3. CRM lead -> client (once).
        MvcResult converted = call(HttpMethod.POST, "/api/v1/crm-leads/" + leadId + "/convert-to-client", adminToken,
                "{\"billingCurrency\":\"EUR\"}", 201);
        String clientId = id(converted);
        assertThat((String) JsonPath.read(body(converted), "$.name")).isEqualTo("Acme " + unique);
        assertThat((String) JsonPath.read(body(converted), "$.contactEmail")).isEqualTo(contactEmail);
        assertThat((String) JsonPath.read(body(converted), "$.billingCurrency")).isEqualTo("EUR");
        MvcResult lead = call(HttpMethod.GET, "/api/v1/crm-leads/" + leadId, adminToken, null, 200);
        assertThat((String) JsonPath.read(body(lead), "$.pipelineStage")).isEqualTo("WON");
        assertThat((String) JsonPath.read(body(lead), "$.convertedClientId")).isEqualTo(clientId);
        call(HttpMethod.POST, "/api/v1/crm-leads/" + leadId + "/convert-to-client", adminToken, "{}", 409);

        // 4. Portal invitation for the client contact.
        MvcResult invited = call(HttpMethod.POST, "/api/v1/admin/users", adminToken, "{\"email\":\"" + contactEmail
                + "\",\"fullName\":\"Olive Owner\",\"role\":\"CLIENT\",\"clientId\":\"" + clientId + "\"}", 201);
        String userId = JsonPath.read(body(invited), "$.user.id").toString();
        String token = JsonPath.read(body(invited), "$.invitation.token");
        assertThat((String) JsonPath.read(body(invited), "$.user.accountStatus")).isEqualTo("INVITED");
        assertThat((String) JsonPath.read(body(invited), "$.user.clientRole")).isEqualTo("CLIENT_OWNER");
        // Only the hash is stored.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_invitations WHERE token_hash = ?", Integer.class,
                token)).isZero();
        // Same email twice -> 409.
        call(HttpMethod.POST, "/api/v1/admin/users", adminToken, "{\"email\":\"" + contactEmail.toUpperCase()
                + "\",\"fullName\":\"Dup\",\"role\":\"CLIENT\",\"clientId\":\"" + clientId + "\"}", 409);

        // Invited accounts cannot log in yet.
        login(contactEmail, "Portal-Password-1", 401);

        // 5. Invitee previews and accepts; the token is single use.
        MvcResult preview = call(HttpMethod.POST, "/api/v1/auth/invitations/preview", null,
                "{\"token\":\"" + token + "\"}", 200);
        assertThat((String) JsonPath.read(body(preview), "$.email")).isEqualTo(contactEmail);
        call(HttpMethod.POST, "/api/v1/auth/invitations/accept", null,
                "{\"token\":\"" + token + "\",\"password\":\"short\"}", 400);
        MvcResult accepted = call(HttpMethod.POST, "/api/v1/auth/invitations/accept", null,
                "{\"token\":\"" + token + "\",\"password\":\"Portal-Password-1\"}", 200);
        assertThat((String) JsonPath.read(body(accepted), "$.user.role")).isEqualTo("CLIENT");
        call(HttpMethod.POST, "/api/v1/auth/invitations/accept", null,
                "{\"token\":\"" + token + "\",\"password\":\"Portal-Password-1\"}", 400);

        // 6. Client logs in and sees only its own (empty) workspace.
        String clientToken = login(contactEmail, "Portal-Password-1", 200);
        MvcResult me = call(HttpMethod.GET, "/api/v1/auth/me", clientToken, null, 200);
        assertThat((String) JsonPath.read(body(me), "$.clientId")).isEqualTo(clientId);
        assertThat((String) JsonPath.read(body(me), "$.fullName")).isEqualTo("Olive Owner");
        assertThat(body(call(HttpMethod.GET, "/api/v1/projects/my", clientToken, null, 200))).isEqualTo("[]");
        call(HttpMethod.GET, "/api/v1/admin/users", clientToken, null, 403);
        call(HttpMethod.GET, "/api/v1/clients", clientToken, null, 403);

        // 7. Suspension revokes existing tokens immediately; re-activation restores access.
        call(HttpMethod.POST, "/api/v1/admin/users/" + userId + "/suspend", adminToken, null, 200);
        call(HttpMethod.GET, "/api/v1/auth/me", clientToken, null, 401);
        login(contactEmail, "Portal-Password-1", 401);
        call(HttpMethod.POST, "/api/v1/admin/users/" + userId + "/reactivate", adminToken, null, 200);
        clientToken = login(contactEmail, "Portal-Password-1", 200);

        // 8. Password change requires the current password.
        call(HttpMethod.POST, "/api/v1/auth/password", clientToken,
                "{\"currentPassword\":\"wrong-password-1\",\"newPassword\":\"Another-Password-2\"}", 400);
        call(HttpMethod.POST, "/api/v1/auth/password", clientToken,
                "{\"currentPassword\":\"Portal-Password-1\",\"newPassword\":\"Another-Password-2\"}", 204);
        login(contactEmail, "Portal-Password-1", 401);
        login(contactEmail, "Another-Password-2", 200);

        // 9. The user list shows the account with its client.
        MvcResult users = call(HttpMethod.GET, "/api/v1/admin/users?role=CLIENT&clientId=" + clientId, adminToken,
                null, 200);
        assertThat((List<?>) JsonPath.read(body(users), "$")).hasSize(1);
        assertThat((String) JsonPath.read(body(users), "$[0].clientName")).isEqualTo("Acme " + unique);
        assertThat((String) JsonPath.read(body(users), "$[0].accountStatus")).isEqualTo("ACTIVE");
    }

    @Test
    void adminsCannotLockThemselvesOutAndInvitationsCanBeReissued() throws Exception {
        String adminId = jdbc.queryForObject("SELECT id::text FROM users WHERE normalized_email = ?", String.class,
                adminEmail);
        call(HttpMethod.POST, "/api/v1/admin/users/" + adminId + "/suspend", adminToken, null, 400);

        String email = "new-admin+" + UUID.randomUUID() + "@example.test";
        MvcResult invited = call(HttpMethod.POST, "/api/v1/admin/users", adminToken,
                "{\"email\":\"" + email + "\",\"fullName\":\"Second Admin\",\"role\":\"ADMIN\"}", 201);
        String userId = JsonPath.read(body(invited), "$.user.id").toString();
        String firstToken = JsonPath.read(body(invited), "$.invitation.token");

        MvcResult reissued = call(HttpMethod.POST, "/api/v1/admin/users/" + userId + "/invitation", adminToken, null,
                200);
        String secondToken = JsonPath.read(body(reissued), "$.token");
        assertThat(secondToken).isNotEqualTo(firstToken);
        // The old link stops working.
        call(HttpMethod.POST, "/api/v1/auth/invitations/accept", null,
                "{\"token\":\"" + firstToken + "\",\"password\":\"Second-Admin-Pass-1\"}", 400);
        call(HttpMethod.POST, "/api/v1/auth/invitations/accept", null,
                "{\"token\":\"" + secondToken + "\",\"password\":\"Second-Admin-Pass-1\"}", 200);

        // A client invitation needs a client; an unknown role value is a 400, never a 500.
        call(HttpMethod.POST, "/api/v1/admin/users", adminToken,
                "{\"email\":\"x" + UUID.randomUUID() + "@example.test\",\"fullName\":\"X\",\"role\":\"CLIENT\"}", 400);
        call(HttpMethod.POST, "/api/v1/admin/users", adminToken,
                "{\"email\":\"y" + UUID.randomUUID() + "@example.test\",\"fullName\":\"Y\",\"role\":\"OWNER\"}", 400);
    }

    @Test
    void adminCreatesAClientDirectly() throws Exception {
        MvcResult created = call(HttpMethod.POST, "/api/v1/clients", adminToken,
                "{\"name\":\"Direct Co\",\"contactEmail\":\"ops@direct.test\",\"website\":\"https://direct.test\","
                        + "\"billingCurrency\":\"GBP\",\"retainerTier\":\"PRO_GROWTH\",\"monthlyRetainerHours\":20}", 201);
        assertThat((String) JsonPath.read(body(created), "$.status")).isEqualTo("ACTIVE");
        assertThat((String) JsonPath.read(body(created), "$.legalName")).isEqualTo("Direct Co");
        call(HttpMethod.POST, "/api/v1/clients", adminToken, "{\"name\":\"\",\"contactEmail\":\"bad\"}", 400);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private String login(String email, String password, int expectedStatus) throws Exception {
        MvcResult result = call(HttpMethod.POST, "/api/v1/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", expectedStatus);
        return expectedStatus == 200 ? JsonPath.read(body(result), "$.accessToken") : null;
    }

    private static String crmLeadJson(String company, String email) {
        return "{\"companyName\":\"" + company + "\",\"website\":\"https://acme.test\",\"industry\":\"Retail\","
                + "\"location\":\"Berlin\",\"contactName\":\"Olive Owner\",\"contactEmail\":\"" + email + "\","
                + "\"source\":\"INBOUND_INQUIRY\",\"targetService\":\"SOFTWARE_DEV\",\"identifiedProblem\":\"Portal\","
                + "\"researchSummary\":\"Inbound\",\"qualificationScore\":70,\"pipelineStage\":\"QUALIFIED\","
                + "\"nextAction\":\"Send proposal\",\"doNotContact\":false}";
    }

    private MvcResult call(HttpMethod method, String path, String token, String json, int expectedStatus)
            throws Exception {
        var builder = request(method, path);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
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
}
