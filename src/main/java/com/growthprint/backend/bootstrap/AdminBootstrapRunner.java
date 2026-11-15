package com.growthprint.backend.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Runs {@link AdminBootstrapService} once at start-up. A bad bootstrap setting stops the application with a clear message. */
@Component
@Order(0)
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService service;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(AdminBootstrapService service,
                                @Value("${app.bootstrap.admin-email:}") String email,
                                @Value("${app.bootstrap.admin-password:}") String password) {
        this.service = service;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.bootstrap(email, password);
    }
}
