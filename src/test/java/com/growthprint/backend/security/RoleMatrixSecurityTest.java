package com.growthprint.backend.security;

import com.growthprint.backend.ai.settings.AiSettingsService;
import com.growthprint.backend.ai.triage.SupportTriageService;
import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.TeamModule;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.service.AccountService;
import com.growthprint.backend.auth.service.AuthService;
import com.growthprint.backend.auth.service.InvitationService;
import com.growthprint.backend.auth.service.LoginThrottle;
import com.growthprint.backend.auth.service.PasswordRecoveryService;
import com.growthprint.backend.client.service.ClientService;
import com.growthprint.backend.config.AccountRecoveryRateLimitConfig;
import com.growthprint.backend.config.InMemoryRateLimiter;
import com.growthprint.backend.config.JwtProperties;
import com.growthprint.backend.config.LoginRateLimitConfig;
import com.growthprint.backend.config.RateLimitConfig;
import com.growthprint.backend.config.SecurityConfig;
import com.growthprint.backend.crm.lead.service.CrmLeadService;
import com.growthprint.backend.invoice.pdf.InvoicePdfService;
import com.growthprint.backend.invoice.service.InvoicePaymentReportService;
import com.growthprint.backend.invoice.service.InvoiceService;
import com.growthprint.backend.lead.service.LeadService;
import com.growthprint.backend.outreach.service.OutreachDraftService;
import com.growthprint.backend.project.service.ProjectService;
import com.growthprint.backend.proposal.service.ProposalService;
import com.growthprint.backend.support.service.SupportRequestService;
import com.growthprint.backend.testsupport.SessionStubs;
import com.growthprint.backend.useradmin.service.UserAdministrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * HTTP-level authorization matrix for EVERY route, with the real SecurityConfig, real JwtAuthenticationFilter
 * and real signed JWTs. Services are mocked, so no database is needed and this always runs.
 * <p>
 * "allowed" means the request passed authentication and authorization (any status except 401/403;
 * e.g. 400 for an empty body is fine). Ownership (client A vs client B) is covered by service tests and the
 * PostgreSQL workflow tests.
 */
@WebMvcTest
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, JwtProperties.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        InMemoryRateLimiter.class, RateLimitConfig.class, LoginRateLimitConfig.class,
        AccountRecoveryRateLimitConfig.class})
@TestPropertySource(properties = {
        // Never depend on .env: fixed test-only secret and issuer.
        "app.security.jwt.secret=role-matrix-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz",
        "app.security.jwt.issuer=growthprint-backend",
        "app.cors.allowed-origins=http://localhost:3000",
        "app.rate-limit.login.max-requests=10000",
        "app.rate-limit.public-inquiries.max-requests=10000",
        "app.rate-limit.account-recovery.max-requests=10000"
})
class RoleMatrixSecurityTest {

    enum Access { PUBLIC, AUTHENTICATED, CLIENT, ADMIN }

    private static final String ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean private AuthService authService;
    @MockitoBean private ClientService clientService;
    @MockitoBean private CrmLeadService crmLeadService;
    @MockitoBean private LeadService leadService;
    @MockitoBean private InvoiceService invoiceService;
    @MockitoBean private InvoicePaymentReportService invoicePaymentReportService;
    @MockitoBean private InvoicePdfService invoicePdfService;
    @MockitoBean private OutreachDraftService outreachDraftService;
    @MockitoBean private ProjectService projectService;
    @MockitoBean private ProposalService proposalService;
    @MockitoBean private SupportRequestService supportRequestService;
    @MockitoBean private AiSettingsService aiSettingsService;
    @MockitoBean private SupportTriageService supportTriageService;
    @MockitoBean private AccountService accountService;
    @MockitoBean private LoginThrottle loginThrottle;
    @MockitoBean private PasswordRecoveryService passwordRecoveryService;
    @MockitoBean private InvitationService invitationService;
    @MockitoBean private UserAdministrationService userAdministrationService;
    @MockitoBean private AccountSessionService accountSessionService;

    /** Modules granted to the TEAM_MEMBER token in the current test. */
    private Set<TeamModule> teamModules = EnumSet.noneOf(TeamModule.class);

    @BeforeEach
    void stubSessions() {
        SessionStubs.acceptAllTokens(accountSessionService, () -> teamModules);
    }

    static Stream<Arguments> routes() {
        return Stream.of(
                // Public
                route("POST", "/api/v1/auth/login", Access.PUBLIC),
                route("POST", "/api/v1/auth/logout", Access.PUBLIC),
                route("POST", "/api/v1/public/inquiries", Access.PUBLIC),
                route("GET", "/actuator/health", Access.PUBLIC),
                // Any authenticated user
                route("GET", "/api/v1/auth/password/policy", Access.PUBLIC),
                route("POST", "/api/v1/auth/password/forgot", Access.PUBLIC),
                route("POST", "/api/v1/auth/password/reset/verify", Access.PUBLIC),
                route("POST", "/api/v1/auth/password/reset", Access.PUBLIC),
                route("POST", "/api/v1/auth/invitations/verify", Access.PUBLIC),
                route("POST", "/api/v1/auth/invitations/accept", Access.PUBLIC),
                route("GET", "/api/v1/auth/me", Access.AUTHENTICATED),
                // Self-service account settings (always the caller's own account)
                route("GET", "/api/v1/account/profile", Access.AUTHENTICATED),
                route("PATCH", "/api/v1/account/profile", Access.AUTHENTICATED),
                route("POST", "/api/v1/account/password", Access.AUTHENTICATED),
                route("POST", "/api/v1/account/sessions/revoke", Access.AUTHENTICATED),
                // User / staff / permission management (ADMIN only, never grantable)
                route("GET", "/api/v1/admin/users", Access.ADMIN),
                route("POST", "/api/v1/admin/users", Access.ADMIN),
                route("GET", "/api/v1/admin/users/" + ID, Access.ADMIN),
                route("PATCH", "/api/v1/admin/users/" + ID + "/status", Access.ADMIN),
                route("PATCH", "/api/v1/admin/users/" + ID + "/role", Access.ADMIN),
                route("PUT", "/api/v1/admin/users/" + ID + "/permissions", Access.ADMIN),
                route("POST", "/api/v1/admin/users/" + ID + "/invitation", Access.ADMIN),
                route("POST", "/api/v1/admin/users/" + ID + "/password-reset-link", Access.ADMIN),
                route("GET", "/api/v1/admin/security/overview", Access.ADMIN),
                // Clients
                route("GET", "/api/v1/clients", Access.ADMIN),
                route("GET", "/api/v1/clients/" + ID, Access.ADMIN),
                route("PATCH", "/api/v1/clients/" + ID, Access.ADMIN),
                // Leads / CRM
                route("GET", "/api/v1/leads", Access.ADMIN),
                route("GET", "/api/v1/crm-leads", Access.ADMIN),
                route("GET", "/api/v1/crm-leads/" + ID, Access.ADMIN),
                route("POST", "/api/v1/crm-leads", Access.ADMIN),
                route("POST", "/api/v1/crm-leads/inquiry/" + ID + "/convert", Access.ADMIN),
                route("PUT", "/api/v1/crm-leads/" + ID + "/pipeline-stage", Access.ADMIN),
                route("DELETE", "/api/v1/crm-leads/" + ID, Access.ADMIN),
                // Projects
                route("GET", "/api/v1/projects", Access.ADMIN),
                route("GET", "/api/v1/projects/my", Access.CLIENT),
                route("GET", "/api/v1/projects/" + ID, Access.ADMIN),
                route("POST", "/api/v1/projects", Access.ADMIN),
                route("PATCH", "/api/v1/projects/" + ID + "/status", Access.ADMIN),
                route("POST", "/api/v1/projects/" + ID + "/milestones", Access.ADMIN),
                // Support requests
                route("GET", "/api/v1/support-requests", Access.ADMIN),
                route("GET", "/api/v1/support-requests/my", Access.CLIENT),
                route("GET", "/api/v1/support-requests/" + ID, Access.ADMIN),
                route("POST", "/api/v1/support-requests", Access.ADMIN),
                route("POST", "/api/v1/support-requests/my", Access.CLIENT),
                route("POST", "/api/v1/support-requests/" + ID + "/classify", Access.ADMIN),
                route("POST", "/api/v1/support-requests/" + ID + "/estimate", Access.ADMIN),
                route("POST", "/api/v1/support-requests/" + ID + "/approve-estimate", Access.ADMIN),
                route("POST", "/api/v1/support-requests/my/" + ID + "/approve-estimate", Access.CLIENT),
                route("PATCH", "/api/v1/support-requests/" + ID + "/status", Access.ADMIN),
                // Invoices
                route("GET", "/api/v1/invoices", Access.ADMIN),
                route("GET", "/api/v1/invoices/my", Access.CLIENT),
                route("GET", "/api/v1/invoices/" + ID, Access.ADMIN),
                route("POST", "/api/v1/invoices", Access.ADMIN),
                route("POST", "/api/v1/invoices/" + ID + "/reconcile", Access.ADMIN),
                // Invoice payment reports (client reports, admin reviews)
                route("POST", "/api/v1/invoices/my/" + ID + "/reconcile", Access.CLIENT),
                route("GET", "/api/v1/invoices/my/" + ID + "/payment-reports", Access.CLIENT),
                route("GET", "/api/v1/invoices/" + ID + "/payment-reports", Access.ADMIN),
                route("POST", "/api/v1/invoices/payment-reports/" + ID + "/verify", Access.ADMIN),
                route("POST", "/api/v1/invoices/payment-reports/" + ID + "/reject", Access.ADMIN),
                // Invoice PDFs (generated on demand)
                route("GET", "/api/v1/invoices/" + ID + "/pdf", Access.ADMIN),
                route("GET", "/api/v1/invoices/my/" + ID + "/pdf", Access.CLIENT),
                // Proposals
                route("GET", "/api/v1/proposals", Access.ADMIN),
                route("GET", "/api/v1/proposals/my", Access.CLIENT),
                route("GET", "/api/v1/proposals/" + ID, Access.ADMIN),
                route("POST", "/api/v1/proposals", Access.ADMIN),
                route("PUT", "/api/v1/proposals/" + ID, Access.ADMIN),
                route("POST", "/api/v1/proposals/" + ID + "/publish", Access.ADMIN),
                route("POST", "/api/v1/proposals/my/" + ID + "/decision", Access.CLIENT),
                // Outreach
                route("GET", "/api/v1/outreach/drafts", Access.ADMIN),
                route("GET", "/api/v1/outreach/drafts/" + ID, Access.ADMIN),
                route("POST", "/api/v1/outreach/drafts/" + ID + "/approve", Access.ADMIN),
                route("POST", "/api/v1/outreach/drafts/" + ID + "/send", Access.ADMIN),
                // AI provider settings and AI support triage (admin only)
                route("GET", "/api/v1/admin/ai/providers", Access.ADMIN),
                route("GET", "/api/v1/admin/ai/settings", Access.ADMIN),
                route("PUT", "/api/v1/admin/ai/settings", Access.ADMIN),
                route("PUT", "/api/v1/admin/ai/settings/api-key", Access.ADMIN),
                route("DELETE", "/api/v1/admin/ai/settings/api-key", Access.ADMIN),
                route("POST", "/api/v1/admin/ai/settings/test", Access.ADMIN),
                route("GET", "/api/v1/admin/ai/models", Access.ADMIN),
                route("POST", "/api/v1/support-requests/" + ID + "/ai/triage", Access.ADMIN)
        );
    }

    @ParameterizedTest(name = "{0} {1} as anonymous")
    @MethodSource("routes")
    void anonymous(String method, String path, Access access) throws Exception {
        int status = perform(method, path, null);
        if (access == Access.PUBLIC) {
            assertThat(status).as("%s %s should be public", method, path).isNotIn(401, 403);
        } else {
            assertThat(status).as("%s %s must require authentication", method, path).isEqualTo(401);
        }
    }

    @ParameterizedTest(name = "{0} {1} as CLIENT")
    @MethodSource("routes")
    void client(String method, String path, Access access) throws Exception {
        int status = perform(method, path, token(UserRole.CLIENT));
        if (access == Access.ADMIN) {
            assertThat(status).as("CLIENT must be forbidden from %s %s", method, path).isEqualTo(403);
        } else {
            assertThat(status).as("CLIENT should reach %s %s", method, path).isNotIn(401, 403);
        }
    }

    @ParameterizedTest(name = "{0} {1} as ADMIN")
    @MethodSource("routes")
    void admin(String method, String path, Access access) throws Exception {
        int status = perform(method, path, token(UserRole.ADMIN));
        if (access == Access.CLIENT) {
            assertThat(status).as("ADMIN must be forbidden from client-only %s %s", method, path).isEqualTo(403);
        } else {
            assertThat(status).as("ADMIN should reach %s %s", method, path).isNotIn(401, 403);
        }
    }

    /**
     * Which module a TEAM_MEMBER needs for an ADMIN route; null = never grantable (finance, AI, user
     * management). This is the documented staff policy (SECURITY.md), written out independently of SecurityConfig.
     */
    static TeamModule moduleFor(String path) {
        if (path.contains("/ai/")) return null;
        if (path.startsWith("/api/v1/clients")) return TeamModule.CLIENTS;
        if (path.startsWith("/api/v1/leads") || path.startsWith("/api/v1/crm-leads")) return TeamModule.CRM;
        if (path.startsWith("/api/v1/projects")) return TeamModule.PROJECTS;
        if (path.startsWith("/api/v1/support-requests")) return TeamModule.SUPPORT;
        if (path.startsWith("/api/v1/proposals")) return TeamModule.PROPOSALS;
        if (path.startsWith("/api/v1/outreach")) return TeamModule.OUTREACH;
        return null;
    }

    @ParameterizedTest(name = "{0} {1} as TEAM_MEMBER without permissions")
    @MethodSource("routes")
    void teamMemberWithoutPermissions(String method, String path, Access access) throws Exception {
        teamModules = EnumSet.noneOf(TeamModule.class);
        int status = perform(method, path, token(UserRole.TEAM_MEMBER));
        if (access == Access.PUBLIC || access == Access.AUTHENTICATED) {
            assertThat(status).as("TEAM_MEMBER should reach %s %s", method, path).isNotIn(401, 403);
        } else {
            assertThat(status).as("TEAM_MEMBER without permissions must be forbidden from %s %s", method, path)
                    .isEqualTo(403);
        }
    }

    @ParameterizedTest(name = "{0} {1} as TEAM_MEMBER with every module")
    @MethodSource("routes")
    void teamMemberWithAllModules(String method, String path, Access access) throws Exception {
        teamModules = EnumSet.allOf(TeamModule.class);
        int status = perform(method, path, token(UserRole.TEAM_MEMBER));
        boolean allowed = access == Access.PUBLIC || access == Access.AUTHENTICATED
                || (access == Access.ADMIN && moduleFor(path) != null);
        if (allowed) {
            assertThat(status).as("TEAM_MEMBER with modules should reach %s %s", method, path).isNotIn(401, 403);
        } else {
            assertThat(status).as("TEAM_MEMBER must be forbidden from %s %s", method, path).isEqualTo(403);
        }
    }

    @Test
    void teamMemberOnlyReachesTheGrantedModule() throws Exception {
        teamModules = EnumSet.of(TeamModule.CRM);
        String token = token(UserRole.TEAM_MEMBER);
        assertThat(perform("GET", "/api/v1/crm-leads", token)).isNotIn(401, 403);
        assertThat(perform("GET", "/api/v1/leads", token)).isNotIn(401, 403);
        assertThat(perform("GET", "/api/v1/clients", token)).isEqualTo(403);
        assertThat(perform("GET", "/api/v1/projects", token)).isEqualTo(403);
        assertThat(perform("GET", "/api/v1/invoices", token)).isEqualTo(403);
        assertThat(perform("GET", "/api/v1/admin/users", token)).isEqualTo(403);
    }

    @Test
    void revokedSessionIsRejected() throws Exception {
        // The account check says "no longer valid" (disabled, role changed or token version bumped).
        org.mockito.Mockito.when(accountSessionService.authoritiesFor(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.Optional.empty());
        assertThat(perform("GET", "/api/v1/clients", token(UserRole.ADMIN))).isEqualTo(401);
        assertThat(perform("GET", "/api/v1/auth/me", token(UserRole.CLIENT))).isEqualTo(401);
    }

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        var response = mvc.perform(request(HttpMethod.GET, "/api/v1/clients")).andReturn().getResponse();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()");
    }

    @Test
    void unknownRoutesAreDeniedEvenForAdmins() throws Exception {
        assertThat(perform("GET", "/api/v1/not-a-route", token(UserRole.ADMIN))).isEqualTo(403);
        assertThat(perform("GET", "/api/v1/not-a-route", null)).isEqualTo(401);
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = token(UserRole.ADMIN);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";
        assertThat(perform("GET", "/api/v1/clients", tampered)).isEqualTo(401);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        JwtProperties other = new JwtProperties();
        other.setSecret("a-completely-different-secret-that-is-long-enough-0123456789");
        other.setIssuer("growthprint-backend");
        String foreign = new JwtTokenProvider(other).generateToken(user(UserRole.ADMIN));
        assertThat(perform("GET", "/api/v1/clients", foreign)).isEqualTo(401);
    }

    @Test
    void unauthorizedResponsesAreRfc9457CompatibleJson() throws Exception {
        var result = mvc.perform(request(HttpMethod.GET, "/api/v1/clients")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        assertThat(result.getResponse().getContentAsString())
                .contains("\"type\":\"about:blank\"", "\"title\":\"Unauthorized\"", "\"status\":401",
                        "\"detail\":\"Authentication required\"", "\"instance\":\"/api/v1/clients\"",
                        "\"message\":\"Authentication required\"");
    }

    private int perform(String method, String path, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path);
        if (!"GET".equals(method) && !"DELETE".equals(method)) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder).andReturn().getResponse().getStatus();
    }

    private String token(UserRole role) {
        return tokenProvider.generateToken(user(role));
    }

    private static User user(UserRole role) {
        UUID id = UUID.randomUUID();
        return new User(id, role + "@example.test", role + "@example.test", "hash", role, AccountStatus.ACTIVE,
                role == UserRole.CLIENT ? UUID.randomUUID() : null, Instant.now(), Instant.now());
    }

    private static Arguments route(String method, String path, Access access) {
        return Arguments.of(method, path, access);
    }
}
