package com.growthprint.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthprint.backend.ai.provider.AiProviderAdapters;
import com.growthprint.backend.ai.provider.AiProviderType;
import com.growthprint.backend.ai.settings.AiCredentialCipher;
import com.growthprint.backend.ai.settings.AiSettings;
import com.growthprint.backend.ai.settings.AiSettingsRepository;
import com.growthprint.backend.ai.settings.AiSettingsService;
import com.growthprint.backend.ai.settings.UpdateAiSettingsCommand;
import com.growthprint.backend.ai.web.AiAdminController;
import com.growthprint.backend.ai.web.AiExceptionHandler;
import com.growthprint.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HTTP-level checks of the AI settings API: the key never comes back in any response, and AI failures use the
 * standard error body with a stable type code. (Who may call these endpoints is covered by RoleMatrixSecurityTest.)
 */
class AiAdminApiTest {

    private static final String KEY = "sk-test-not-a-real-key-ABCD9876";
    private final UUID admin = UUID.randomUUID();
    private AiSettings stored = AiSettings.defaults();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AiSettingsRepository repository = mock(AiSettingsRepository.class);
        when(repository.findById(AiSettings.SINGLETON_ID)).thenAnswer(i -> Optional.of(stored));
        when(repository.save(any(AiSettings.class))).thenAnswer(i -> stored = i.getArgument(0));
        AiSettingsService service = new AiSettingsService(repository,
                new AiCredentialCipher(AiCredentialCipherTest.randomKey()), new AiProviderAdapters(new ObjectMapper()),
                Clock.systemUTC());
        service.update(new UpdateAiSettingsCommand(false, AiProviderType.GEMINI, "gemini-test", null, 30, 800, 50), admin);

        mvc = MockMvcBuilders.standaloneSetup(new AiAdminController(service))
                .setControllerAdvice(new AiExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theApiKeyNeverAppearsInAnyResponse() throws Exception {
        String saved = mvc.perform(put("/api/v1/admin/ai/settings/api-key").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + KEY + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String read = mvc.perform(get("/api/v1/admin/ai/settings")).andReturn().getResponse().getContentAsString();

        assertThat(saved).doesNotContain(KEY).contains("\"apiKeyMasked\":\"••••9876\"", "\"apiKeyConfigured\":true");
        assertThat(read).doesNotContain(KEY).doesNotContain("ciphertext");
    }

    @Test
    void turningAiOnWithoutAKeyGivesAClearConflictWithAStableTypeCode() throws Exception {
        var response = mvc.perform(put("/api/v1/admin/ai/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"provider\":\"GEMINI\",\"model\":\"gemini-test\","
                                + "\"timeoutSeconds\":30,\"maxOutputTokens\":800,\"dailyRequestLimitPerUser\":50}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString())
                .contains("\"type\":\"urn:growthprint:ai:KEY_MISSING\"", "\"message\":\"Add an API key");
    }

    @Test
    void invalidSettingsAreRejectedWithFieldErrors() throws Exception {
        var response = mvc.perform(put("/api/v1/admin/ai/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"provider\":\"OPENAI\",\"model\":\"bad model id\","
                                + "\"timeoutSeconds\":1,\"maxOutputTokens\":800,\"dailyRequestLimitPerUser\":50}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("fieldErrors", "model", "timeoutSeconds");
    }

    @Test
    void connectionTestWithoutAKeyIsAConflictNotAProviderCall() throws Exception {
        var response = mvc.perform(post("/api/v1/admin/ai/settings/test")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("urn:growthprint:ai:KEY_MISSING");
    }
}
