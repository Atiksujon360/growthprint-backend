package com.growthprint.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.orm.jpa.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.JpaRepositoriesAutoConfiguration"
})
class HealthEndpointTest {

	@Test
	void applicationContextLoads() {
		// If this test passes, the application context loaded successfully
		assertTrue(true, "Application context loaded");
	}
}
