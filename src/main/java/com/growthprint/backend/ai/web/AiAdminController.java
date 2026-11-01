package com.growthprint.backend.ai.web;

import com.growthprint.backend.ai.provider.AiProviderType;
import com.growthprint.backend.ai.settings.AiSettingsService;
import com.growthprint.backend.ai.settings.AiSettingsView;
import com.growthprint.backend.ai.settings.UpdateAiSettingsCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Admin-only AI provider configuration (Settings → AI providers). */
@RestController
@RequestMapping("/api/v1/admin/ai")
@Tag(name = "AI settings")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AiAdminController {

    public record ProviderOption(AiProviderType id, String displayName, boolean requiresBaseUrl, boolean supportsModelListing) {
    }

    public record SetApiKeyCommand(
            @NotBlank(message = "Enter the API key")
            @Size(max = 500, message = "The API key is too long")
            String apiKey) {
        @Override
        public String toString() {
            return "SetApiKeyCommand[apiKey=****]";
        }
    }

    private final AiSettingsService settings;

    public AiAdminController(AiSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/providers")
    @Operation(summary = "List supported AI providers and what each one needs")
    public List<ProviderOption> providers() {
        return Arrays.stream(AiProviderType.values())
                .map(type -> new ProviderOption(type, type.displayName(), type.requiresCustomBaseUrl(), true))
                .toList();
    }

    @GetMapping("/settings")
    @Operation(summary = "Get the AI configuration (the API key is never returned)")
    public AiSettingsView get() {
        return settings.get();
    }

    @PutMapping("/settings")
    @Operation(summary = "Update provider, model, limits and the on/off switch",
            description = "Changing the provider removes the saved API key. AI can only be turned on once a "
                    + "provider, model and key are set.")
    public AiSettingsView update(@Valid @RequestBody UpdateAiSettingsCommand command, @AuthenticationPrincipal String userId) {
        return settings.update(command, UUID.fromString(userId));
    }

    @PutMapping("/settings/api-key")
    @Operation(summary = "Save or replace the API key (stored encrypted; write-only)")
    public AiSettingsView setApiKey(@Valid @RequestBody SetApiKeyCommand command, @AuthenticationPrincipal String userId) {
        return settings.setApiKey(command.apiKey(), UUID.fromString(userId));
    }

    @DeleteMapping("/settings/api-key")
    @Operation(summary = "Remove the API key (also turns AI off)")
    public AiSettingsView removeApiKey(@AuthenticationPrincipal String userId) {
        return settings.removeApiKey(UUID.fromString(userId));
    }

    @PostMapping("/settings/test")
    @Operation(summary = "Test the connection to the configured provider",
            description = "Makes one very small request to the provider, which may count towards its usage.")
    public AiSettingsView testConnection(@AuthenticationPrincipal String userId) {
        return settings.testConnection(UUID.fromString(userId));
    }

    @GetMapping("/models")
    @Operation(summary = "List models available to the saved key (where the provider supports it)")
    public List<String> models() {
        return settings.listModels();
    }
}
