package com.growthprint.backend.ai.web;

import com.growthprint.backend.ai.triage.SupportTriageService;
import com.growthprint.backend.ai.triage.SupportTriageSuggestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** AI help for the admin support queue. Suggestions only: nothing is saved or sent. */
@RestController
@Tag(name = "Support Requests")
@SecurityRequirement(name = "bearerAuth")
public class SupportTriageController {

    private final SupportTriageService triage;

    public SupportTriageController(SupportTriageService triage) {
        this.triage = triage;
    }

    @PostMapping("/api/v1/support-requests/{id}/ai/triage")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Suggest a category, urgency, summary and reply draft with AI",
            description = "Sends only the request's title, description, category, impact and urgency to the configured AI "
                    + "provider. Returns 409 when AI is turned off or not set up. Nothing is applied or sent automatically.")
    public SupportTriageSuggestion suggest(@PathVariable UUID id, @AuthenticationPrincipal String userId) {
        return triage.suggest(id, UUID.fromString(userId));
    }
}
