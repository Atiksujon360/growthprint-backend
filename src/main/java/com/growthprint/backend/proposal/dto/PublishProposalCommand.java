package com.growthprint.backend.proposal.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PublishProposalCommand(
        @NotNull(message = "Valid until date is required")
        @Future(message = "Valid until date must be in the future")
        LocalDate validUntil
) {}
