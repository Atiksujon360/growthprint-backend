package com.growthprint.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.orm.jpa.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.JpaRepositoriesAutoConfiguration"
})
class GrowthprintBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
