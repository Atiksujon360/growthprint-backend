package com.growthprint.backend.client.controller;

import com.growthprint.backend.client.dto.ClientResponse;
import com.growthprint.backend.client.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @Operation(summary = "List all clients")
    public List<ClientResponse> list() {
        return clientService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID")
    public ClientResponse getById(@PathVariable UUID id) {
        return clientService.findById(id);
    }
}
