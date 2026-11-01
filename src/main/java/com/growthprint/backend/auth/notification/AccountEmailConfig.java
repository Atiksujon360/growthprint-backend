package com.growthprint.backend.auth.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the no-op sender unless a real {@link AccountEmailSender} bean is provided. */
@Configuration
public class AccountEmailConfig {

    @Bean
    @ConditionalOnMissingBean(AccountEmailSender.class)
    public AccountEmailSender unconfiguredAccountEmailSender() {
        return new UnconfiguredAccountEmailSender();
    }
}
