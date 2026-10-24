package com.growthprint.backend.client.controller;

import com.growthprint.backend.client.dto.ClientResponse;
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
    @PreAuthorize("hasRole('ADMIN')")
    public List<ClientResponse> list() {
        return clientService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public ClientResponse getById(@PathVariable UUID id) {
        return clientService.findById(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update client", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    public ClientResponse update(@PathVariable UUID id, @RequestBody java.util.Map<String, Object> updates) {
        return clientService.update(id, updates);
    }
}
