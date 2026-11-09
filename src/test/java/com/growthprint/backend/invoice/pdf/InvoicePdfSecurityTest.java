package com.growthprint.backend.invoice.pdf;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.config.AccountRecoveryRateLimitConfig;
import com.growthprint.backend.config.InMemoryRateLimiter;
import com.growthprint.backend.config.JwtProperties;
import com.growthprint.backend.config.LoginRateLimitConfig;
import com.growthprint.backend.config.RateLimitConfig;
import com.growthprint.backend.config.SecurityConfig;
import com.growthprint.backend.security.AccountSessionService;
import com.growthprint.backend.security.JwtAccessDeniedHandler;
import com.growthprint.backend.testsupport.SessionStubs;
import com.growthprint.backend.security.JwtAuthenticationEntryPoint;
import com.growthprint.backend.security.JwtAuthenticationFilter;
import com.growthprint.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real SecurityConfig + JWT filter + signed tokens: who may download which invoice PDF. No database. */
@WebMvcTest(controllers = InvoicePdfController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, JwtProperties.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        InMemoryRateLimiter.class, RateLimitConfig.class, LoginRateLimitConfig.class,
        AccountRecoveryRateLimitConfig.class})
@TestPropertySource(properties = {
        "app.security.jwt.secret=invoice-pdf-security-test-secret-0123456789-abcdefghijklmnop",
        "app.security.jwt.issuer=growthprint-backend",
        "app.cors.allowed-origins=http://localhost:3000",
        "app.rate-limit.login.max-requests=10000",
        "app.rate-limit.public-inquiries.max-requests=10000"
})
class InvoicePdfSecurityTest {

    private static final String ID = UUID.randomUUID().toString();
    private static final String ADMIN_PATH = "/api/v1/invoices/" + ID + "/pdf";
    private static final String CLIENT_PATH = "/api/v1/invoices/my/" + ID + "/pdf";

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @MockitoBean private InvoicePdfService pdfService;
    @MockitoBean private AccountSessionService accountSessionService;

    @BeforeEach
    void setUp() {
        InvoicePdf pdf = new InvoicePdf("GrowthPrint-Invoice-INV-1.pdf", "%PDF-1.4".getBytes());
        when(pdfService.renderForAdmin(any())).thenReturn(pdf);
        when(pdfService.renderForClient(any(), anyString())).thenReturn(pdf);
        SessionStubs.acceptAllTokens(accountSessionService);
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get(ADMIN_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(CLIENT_PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotUseTheAdminEndpoint() throws Exception {
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(UserRole.CLIENT))).andExpect(status().isForbidden());
    }

    @Test
    void adminCannotUseTheClientEndpoint() throws Exception {
        mvc.perform(get(CLIENT_PATH).header("Authorization", bearer(UserRole.ADMIN))).andExpect(status().isForbidden());
    }

    @Test
    void eachRoleReachesItsOwnEndpoint() throws Exception {
        mvc.perform(get(ADMIN_PATH).header("Authorization", bearer(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
        mvc.perform(get(CLIENT_PATH).header("Authorization", bearer(UserRole.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    private String bearer(UserRole role) {
        User user = new User(UUID.randomUUID(), role + "@example.test", role + "@example.test", "hash", role,
                AccountStatus.ACTIVE, role == UserRole.CLIENT ? UUID.randomUUID() : null, Instant.now(), Instant.now());
        return "Bearer " + tokenProvider.generateToken(user);
    }
}
