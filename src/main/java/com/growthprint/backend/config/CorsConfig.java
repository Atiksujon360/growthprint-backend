package com.growthprint.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * General application beans.
 * <p>
 * CORS itself is configured once, in {@link SecurityConfig#corsConfigurationSource()}.
 * A second standalone CorsFilter used to live here; it was removed because two CORS
 * configurations could disagree and the Spring Security one always won anyway.
 */
@Configuration
public class CorsConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
