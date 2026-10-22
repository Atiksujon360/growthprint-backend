package com.growthprint.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ComponentScan(basePackages = "com.growthprint.backend", excludeFilters = {
		@ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.growthprint\\.backend\\.(crm|client|lead|inquiry)\\..*")
})
class GrowthprintBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
