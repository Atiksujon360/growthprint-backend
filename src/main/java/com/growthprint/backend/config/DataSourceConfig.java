package com.growthprint.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

@Configuration
@ConditionalOnClass(name = "org.springframework.boot.jdbc.DataSourceBuilder")
@ConditionalOnProperty(name = "DATABASE_URL")
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        String url = env.getProperty("DATABASE_URL");
        
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("DATABASE_URL environment variable is required but not set");
        }

        // Normalize the URL if needed
        String normalizedUrl = normalizeDatabaseUrl(url);
        
        // If the original URL had embedded credentials, we need to extract them
        // and set them separately since the application.yml expects separate username/password
        if (hasEmbeddedCredentials(url)) {
            // The URL already has credentials embedded, so we'll use it as-is
            // Spring Boot will parse the credentials from the URL
            return DataSourceBuilder.create()
                    .url(normalizedUrl)
                    .driverClassName("org.postgresql.Driver")
                    .build();
        } else {
            // Use separate username/password from environment
            String username = env.getProperty("DATABASE_USERNAME");
            String password = env.getProperty("DATABASE_PASSWORD");
            
            if (username == null || username.isBlank()) {
                throw new IllegalStateException("DATABASE_USERNAME environment variable is required but not set");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("DATABASE_PASSWORD environment variable is required but not set");
            }
            
            return DataSourceBuilder.create()
                    .url(normalizedUrl)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();
        }
    }

    String normalizeDatabaseUrl(String url) {
        // Convert postgres:// or postgresql:// to jdbc:postgresql://
        if (url.startsWith("postgres://")) {
            url = "jdbc:postgresql://" + url.substring("postgres://".length());
        } else if (url.startsWith("postgresql://")) {
            url = "jdbc:postgresql://" + url.substring("postgresql://".length());
        }
        
        // Ensure SSL mode is present for Render connections
        if (!url.contains("sslmode=") && (url.contains("render.com") || url.contains("render"))) {
            if (url.contains("?")) {
                url = url + "&sslmode=require";
            } else {
                url = url + "?sslmode=require";
            }
        }
        
        return url;
    }

    boolean hasEmbeddedCredentials(String url) {
        // Check if URL contains username:password@ pattern
        // Pattern: jdbc:postgresql://username:password@host/...
        // or postgres://username:password@host/...
        String afterPrefix;
        if (url.startsWith("jdbc:postgresql://")) {
            afterPrefix = url.substring("jdbc:postgresql://".length());
        } else if (url.startsWith("postgres://")) {
            afterPrefix = url.substring("postgres://".length());
        } else if (url.startsWith("postgresql://")) {
            afterPrefix = url.substring("postgresql://".length());
        } else {
            return false;
        }
        
        // Check if there's a @ after the credentials
        int atIndex = afterPrefix.indexOf('@');
        if (atIndex > 0) {
            String beforeAt = afterPrefix.substring(0, atIndex);
            // Check if there's a colon (password separator) before the @
            return beforeAt.contains(":");
        }
        
        return false;
    }
}
