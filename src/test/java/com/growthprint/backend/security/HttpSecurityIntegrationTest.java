package com.growthprint.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.client.controller.ClientController;
import com.growthprint.backend.client.service.ClientService;
import com.growthprint.backend.config.JwtProperties;
import com.growthprint.backend.config.SecurityConfig;
import com.growthprint.backend.crm.lead.controller.CrmLeadController;
import com.growthprint.backend.crm.lead.service.CrmLeadService;
import com.growthprint.backend.support.controller.SupportRequestController;
import com.growthprint.backend.support.service.SupportRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ClientController.class, CrmLeadController.class, SupportRequestController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAccessDeniedHandler.class, JwtAuthenticationEntryPoint.class})
class HttpSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClientService clientService;

    @MockBean
    private CrmLeadService crmLeadService;

    @MockBean
    private SupportRequestService supportRequestService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RateLimitFilter rateLimitFilter;

    @MockBean
    private CorrelationIdFilter correlationIdFilter;

    @MockBean
    private JwtProperties jwtProperties;

    private UUID testClientId;
    private UUID testLeadId;
    private UUID testRequestId;

    @BeforeEach
    void setUp() {
        testClientId = UUID.randomUUID();
        testLeadId = UUID.randomUUID();
        testRequestId = UUID.randomUUID();

        // Configure jwtProperties to pass validation
        when(jwtProperties.getSecret()).thenReturn("test-secret-key-at-least-32-characters-long-for-testing");
        when(jwtProperties.getExpiration()).thenReturn(java.time.Duration.ofHours(1));
        when(jwtProperties.getIssuer()).thenReturn("growthprint-backend-test");
    }

    // ==================== PATCH /api/v1/clients/{id} ====================

    @Test
    void patchClient_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/clients/{id}", testClientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Updated")))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(clientService, never()).update(any(), any());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void patchClient_WithNonAdminRole_Returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/clients/{id}", testClientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Updated")))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(clientService, never()).update(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void patchClient_WithAdminRole_ReachesService() throws Exception {
        mockMvc.perform(patch("/api/v1/clients/{id}", testClientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Updated")))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(clientService).update(eq(testClientId), any());
    }

    // ==================== DELETE /api/v1/crm-leads/{id} ====================

    @Test
    void deleteLead_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/crm-leads/{id}", testLeadId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(crmLeadService, never()).delete(any());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void deleteLead_WithNonAdminRole_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/crm-leads/{id}", testLeadId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(crmLeadService, never()).delete(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteLead_WithAdminRole_ReachesService() throws Exception {
        mockMvc.perform(delete("/api/v1/crm-leads/{id}", testLeadId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(crmLeadService).delete(testLeadId);
    }

    // ==================== POST /api/v1/support-requests/{id}/estimate ====================

    @Test
    void attachEstimate_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/support-requests/{id}/estimate", testRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(supportRequestService, never()).attachEstimate(any(), any());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void attachEstimate_WithNonAdminRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/support-requests/{id}/estimate", testRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(supportRequestService, never()).attachEstimate(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void attachEstimate_WithAdminRole_ReachesService() throws Exception {
        mockMvc.perform(post("/api/v1/support-requests/{id}/estimate", testRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(supportRequestService).attachEstimate(eq(testRequestId), any());
    }

    // ==================== POST /api/v1/support-requests/{id}/approve-estimate ====================

    @Test
    void approveEstimate_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/support-requests/{id}/approve-estimate", testRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(supportRequestService, never()).approveEstimate(any());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void approveEstimate_WithNonAdminRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/support-requests/{id}/approve-estimate", testRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(supportRequestService, never()).approveEstimate(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approveEstimate_WithAdminRole_ReachesService() throws Exception {
        mockMvc.perform(post("/api/v1/support-requests/{id}/approve-estimate", testRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(supportRequestService).approveEstimate(eq(testRequestId), any());
    }
}
