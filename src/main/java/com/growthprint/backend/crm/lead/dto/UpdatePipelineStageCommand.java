package com.growthprint.backend.crm.lead.dto;

import com.growthprint.backend.crm.lead.entity.CrmLeadEnums;
import jakarta.validation.constraints.NotNull;

public record UpdatePipelineStageCommand(
        @NotNull CrmLeadEnums.CrmLeadPipelineStage pipelineStage
) {
}
