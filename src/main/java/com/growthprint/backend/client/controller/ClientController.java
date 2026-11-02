package com.growthprint.backend.client.controller;

import com.growthprint.backend.client.dto.ClientResponse;
import com.growthprint.backend.client.dto.UpdateClientCommand;
import com.growthprint.backend.client.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    @Operation(summary = "List all clients", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CLIENTS')")
    public List<ClientResponse> list() {
        return clientService.findAll();
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Create a client", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CLIENTS')")
    public ClientResponse create(@org.springframework.security.core.annotation.AuthenticationPrincipal String userId,
                                 @jakarta.validation.Valid @RequestBody
                                 com.growthprint.backend.client.dto.CreateClientCommand command) {
        return clientService.create(command, "GrowthPrint");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CLIENTS')")
    public ClientResponse getById(@PathVariable UUID id) {
        return clientService.findById(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update client (partial)", security = @SecurityRequirement(name = "bearerAuth"),
            description = "Updatable fields: name, contactEmail, phone (alias: contactPhone), notes. "
                    + "Omitted fields are unchanged; an empty phone clears it.")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_CLIENTS')")
    public ClientResponse update(@PathVariable UUID id,
                                 @jakarta.validation.Valid @RequestBody UpdateClientCommand updates) {
        return clientService.update(id, updates);
    }
}
