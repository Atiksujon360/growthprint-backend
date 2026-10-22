package com.growthprint.backend.crm.lead.repository;

import com.growthprint.backend.crm.lead.entity.CrmLead;
import com.growthprint.backend.crm.lead.entity.CrmLeadEnums;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Disabled("Requires Docker - excluded from default test run. Run with -Dtest=CrmLeadRepositoryIntegrationTest to enable.")
class CrmLeadRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	// Resource leak warning suppressed: Testcontainers @Container annotation manages lifecycle automatically
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:16-alpine"))
			.withDatabaseName("growthprint")
			.withUsername("testuser")
			.withPassword("testpass");

	@Autowired
	private CrmLeadRepository crmLeadRepository;

	@Test
	void saveAndRetrieveCrmLead() {
		// Given
		UUID id = UUID.randomUUID();
		CrmLead lead = new CrmLead(
				id,
				"Test Company",
				"https://testcompany.com",
				"Technology",
				"San Francisco, CA",
				"John Doe",
				"john@testcompany.com",
				"CTO",
				CrmLeadEnums.CrmLeadSource.INBOUND_INQUIRY,
				"https://testcompany.com/inquiry",
				Instant.now(),
				CrmLeadEnums.CrmLeadServiceCategory.SOFTWARE_DEV,
				"Need web application",
				"Research summary",
				75,
				20,
				18,
				19,
				18,
				CrmLeadEnums.CrmLeadPipelineStage.QUALIFIED,
				null,
				"Schedule demo",
				false,
				"Initial contact",
				null,
				Instant.now(),
				Instant.now()
		);

		// When
		crmLeadRepository.save(lead);
		Optional<CrmLead> retrieved = crmLeadRepository.findById(id);

		// Then
		assertTrue(retrieved.isPresent(), "CRM lead should be retrieved");
		CrmLead retrievedLead = retrieved.get();
		assertEquals("Test Company", retrievedLead.getCompanyName());
		assertEquals("john@testcompany.com", retrievedLead.getContactEmail());
		assertEquals(CrmLeadEnums.CrmLeadPipelineStage.QUALIFIED, retrievedLead.getPipelineStage());
		assertEquals(75, retrievedLead.getQualificationScore());
	}

	@Test
	void findAllReturnsAllLeads() {
		// Given
		CrmLead lead1 = new CrmLead(
				UUID.randomUUID(),
				"Company A",
				"https://companya.com",
				"Technology",
				"New York",
				"Alice",
				"alice@companya.com",
				"CEO",
				CrmLeadEnums.CrmLeadSource.PUBLIC_DIRECTORY,
				null,
				Instant.now(),
				CrmLeadEnums.CrmLeadServiceCategory.WEBSITE_MAINTENANCE,
				"Problem A",
				"Summary A",
				60,
				15,
				15,
				15,
				15,
				CrmLeadEnums.CrmLeadPipelineStage.DISCOVERED,
				null,
				"Reach out",
				false,
				null,
				null,
				Instant.now(),
				Instant.now()
		);

		CrmLead lead2 = new CrmLead(
				UUID.randomUUID(),
				"Company B",
				"https://companyb.com",
				"Finance",
				"London",
				"Bob",
				"bob@companyb.com",
				"Manager",
				CrmLeadEnums.CrmLeadSource.REFERRAL,
				null,
				Instant.now(),
				CrmLeadEnums.CrmLeadServiceCategory.TECHNICAL_SEO,
				"Problem B",
				"Summary B",
				85,
				22,
				21,
				21,
				21,
				CrmLeadEnums.CrmLeadPipelineStage.READY_FOR_OUTREACH,
				null,
				"Send proposal",
				false,
				null,
				null,
				Instant.now(),
				Instant.now()
		);

		crmLeadRepository.save(lead1);
		crmLeadRepository.save(lead2);

		// When
		List<CrmLead> leads = crmLeadRepository.findAll();

		// Then
		assertTrue(leads.size() >= 2, "Should return at least 2 leads");
	}

	@Test
	void deleteCrmLead() {
		// Given
		UUID id = UUID.randomUUID();
		CrmLead lead = new CrmLead(
				id,
				"Delete Test Company",
				"https://deletetest.com",
				"Healthcare",
				"Boston",
				"Diana",
				"diana@deletetest.com",
				"VP",
				CrmLeadEnums.CrmLeadSource.INBOUND_INQUIRY,
				null,
				Instant.now(),
				CrmLeadEnums.CrmLeadServiceCategory.AI_INTEGRATION,
				"AI problem",
				"AI summary",
				70,
				18,
				17,
				18,
				17,
				CrmLeadEnums.CrmLeadPipelineStage.LOST,
				null,
				"Archive",
				false,
				null,
				null,
				Instant.now(),
				Instant.now()
		);

		crmLeadRepository.save(lead);
		assertTrue(crmLeadRepository.findById(id).isPresent(), "Lead should exist before deletion");

		// When
		crmLeadRepository.deleteById(id);

		// Then
		assertFalse(crmLeadRepository.findById(id).isPresent(), "Lead should be deleted");
	}
}
