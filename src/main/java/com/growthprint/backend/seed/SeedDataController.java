package com.growthprint.backend.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST endpoint to manually trigger seed data creation.
 * Only available in dev profile.
 */
@RestController
@RequestMapping("/api/v1/dev/seed")
@Profile({ "dev", "development" })
public class SeedDataController {

    private static final Logger logger = LoggerFactory.getLogger(SeedDataController.class);

    private final ObjectProvider<SeedDataCommand> seedDataCommandProvider;

    public SeedDataController(ObjectProvider<SeedDataCommand> seedDataCommandProvider) {
        this.seedDataCommandProvider = seedDataCommandProvider;
    }

    @PostMapping
    public Map<String, Object> seedData() {
        logger.info("Manual seed data trigger received");

        Map<String, Object> response = new HashMap<>();

        SeedDataCommand seedDataCommand = seedDataCommandProvider.getIfAvailable();
        if (seedDataCommand == null) {
            response.put("status", "error");
            response.put("message", "SeedDataCommand not available. The 'seed' profile must be enabled. Restart the application with --spring.profiles.active=seed,dev");
            return response;
        }

        try {
            seedDataCommand.run();
            response.put("status", "success");
            response.put("message", "Seed data created successfully");
        } catch (Exception e) {
            logger.error("Error seeding data", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return response;
    }
}
