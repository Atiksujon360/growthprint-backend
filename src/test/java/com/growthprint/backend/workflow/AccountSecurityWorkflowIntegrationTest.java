package com.growthprint.backend.workflow;

import com.growthprint.backend.auth.notification.AccountEmailSender;
import com.growthprint.backend.testsupport.AbstractPostgresIntegrationTest;
import com.growthprint.backend.testsupport.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Password recovery, password change, session revocation and staff permissions over HTTP against real PostgreSQL
 * (V20 schema). The email sender is a mock that captures the reset link, as a real provider would receive it.
 */
@PostgresIntegrationTest
class AccountSecurityWorkflowIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Original-Pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private AccountEmailSender emailSender;

    private String email;
    private String userId;

    @DynamicPropertySource
    static void limits(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.account-recovery.max-requests", () -> "1000");
        registry.add("app.security.account.reset-max-requests-per-account", () -> "1000");
        registry.add("app.security.account.login-max-attempts-per-account", () -> "1000");
    }

    @BeforeEach
    void createUser() {
        email = "owner+" + UUID.randomUUID() + "@example.test";
        userId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users (id, email, normalized_email, password_hash, role, account_status, full_name) "
                        + "VALUES (?::uuid, ?, ?, ?, 'ADMIN', 'ACTIVE', 'Owner')",
                userId, email, email, passwordEncoder.encode(PASSWORD));
        clearInvocations(emailSender);
    }

    @Test
    void forgotPasswordAnswersIdenticallyForKnownAndUnknownEmails() throws Exception {
        String unknown = body(call(HttpMethod.POST, "/api/v1/auth/password/forgot", null,
                "{\"email\":\"nobody+" + UUID.randomUUID() + "@example.test\"}", 202));
        verify(emailSender, never()).sendPasswordReset(any(), any(), any(), any());

        String known = body(call(HttpMethod.POST, "/api/v1/auth/password/forgot", null,
                "{\"email\":\"" + email + "\"}", 202));
        assertThat(known).isEqualTo(unknown);
        verify(emailSender).sendPasswordReset(eq(email), any(), any(), any());
    }

    @Test
    void resetLinkIsSingleUseAndRevokesExistingSessions() throws Exception {
        String oldToken = login(email, PASSWORD, 200);
        String rawToken = requestResetToken();

        // Only the hash is stored
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash = ?",
                Integer.class, rawToken)).isZero();

        call(HttpMethod.POST, "/api/v1/auth/password/reset/verify", null, "{\"token\":\"" + rawToken + "\"}", 204);
        call(HttpMethod.POST, "/api/v1/auth/password/reset", null,
                "{\"token\":\"" + rawToken + "\",\"newPassword\":\"short\"}", 400);
        call(HttpMethod.POST, "/api/v1/auth/password/reset", null,
                "{\"token\":\"" + rawToken + "\",\"newPassword\":\"Brand-New-Pass-456\"}", 204);
        // Reuse fails
        call(HttpMethod.POST, "/api/v1/auth/password/reset", null,
                "{\"token\":\"" + rawToken + "\",\"newPassword\":\"Another-Pass-789\"}", 400);

        call(HttpMethod.GET, "/api/v1/auth/me", oldToken, null, 401);
        login(email, PASSWORD, 401);
        login(email, "Brand-New-Pass-456", 200);
    }

    @Test
    void expiredResetLinkIsRejected() throws Exception {
        String rawToken = requestResetToken();
        jdbc.update("UPDATE password_reset_tokens SET created_at = now() - interval '2 hours', "
                + "expires_at = now() - interval '1 hour' WHERE user_id = ?::uuid", userId);
        call(HttpMethod.POST, "/api/v1/auth/password/reset/verify", null, "{\"token\":\"" + rawToken + "\"}", 400);
        call(HttpMethod.POST, "/api/v1/auth/password/reset", null,
                "{\"token\":\"" + rawToken + "\",\"newPassword\":\"Brand-New-Pass-456\"}", 400);
    }

    @Test
    void changePasswordKeepsThisDeviceAndRevokesOthers() throws Exception {
        String otherDevice = login(email, PASSWORD, 200);
        String thisDevice = login(email, PASSWORD, 200);

        call(HttpMethod.POST, "/api/v1/account/password", thisDevice,
                "{\"currentPassword\":\"wrong-password-1\",\"newPassword\":\"Brand-New-Pass-456\"}", 400);
        MvcResult changed = call(HttpMethod.POST, "/api/v1/account/password", thisDevice,
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Brand-New-Pass-456\"}", 200);
        String fresh = JsonPath.read(body(changed), "$.accessToken");

        call(HttpMethod.GET, "/api/v1/auth/me", otherDevice, null, 401);
        call(HttpMethod.GET, "/api/v1/auth/me", thisDevice, null, 401);
        call(HttpMethod.GET, "/api/v1/auth/me", fresh, null, 200);

        call(HttpMethod.POST, "/api/v1/account/sessions/revoke", fresh, null, 204);
        call(HttpMethod.GET, "/api/v1/auth/me", fresh, null, 401);
    }

    @Test
    void teamMemberOnlyReachesGrantedModules() throws Exception {
        String adminToken = login(email, PASSWORD, 200);
        String staffEmail = "staff+" + UUID.randomUUID() + "@example.test";
        MvcResult invited = call(HttpMethod.POST, "/api/v1/admin/users", adminToken, "{\"email\":\"" + staffEmail
                + "\",\"fullName\":\"Sam Staff\",\"role\":\"TEAM_MEMBER\",\"permissions\":[\"CRM\"]}", 201);
        String staffId = JsonPath.read(body(invited), "$.user.id");
        String inviteToken = JsonPath.read(body(invited), "$.invitation.token");

        MvcResult accepted = call(HttpMethod.POST, "/api/v1/auth/invitations/accept", null,
                "{\"token\":\"" + inviteToken + "\",\"password\":\"Staff-Password-1\"}", 200);
        String staffToken = JsonPath.read(body(accepted), "$.accessToken");

        call(HttpMethod.GET, "/api/v1/crm-leads", staffToken, null, 200);
        call(HttpMethod.GET, "/api/v1/clients", staffToken, null, 403);
        call(HttpMethod.GET, "/api/v1/invoices", staffToken, null, 403);
        call(HttpMethod.GET, "/api/v1/admin/users", staffToken, null, 403);

        // Revoking the module applies on the next request, without a new login
        call(HttpMethod.PUT, "/api/v1/admin/users/" + staffId + "/permissions", adminToken,
                "{\"modules\":[\"PROJECTS\"]}", 200);
        call(HttpMethod.GET, "/api/v1/crm-leads", staffToken, null, 403);
        call(HttpMethod.GET, "/api/v1/projects", staffToken, null, 200);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private String requestResetToken() throws Exception {
        clearInvocations(emailSender);
        call(HttpMethod.POST, "/api/v1/auth/password/forgot", null, "{\"email\":\"" + email + "\"}", 202);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendPasswordReset(eq(email), any(), link.capture(), any());
        assertThat(link.getValue()).contains("/reset-password#token=");
        return link.getValue().substring(link.getValue().indexOf("#token=") + "#token=".length());
    }

    private String login(String loginEmail, String password, int expectedStatus) throws Exception {
        MvcResult result = call(HttpMethod.POST, "/api/v1/auth/login", null,
                "{\"email\":\"" + loginEmail + "\",\"password\":\"" + password + "\"}", expectedStatus);
        return expectedStatus == 200 ? JsonPath.read(body(result), "$.accessToken") : null;
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
}
