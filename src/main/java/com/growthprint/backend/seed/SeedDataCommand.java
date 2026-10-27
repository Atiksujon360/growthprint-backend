package com.growthprint.backend.seed;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.entity.ClientEnums;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.crm.lead.entity.CrmLead;
import com.growthprint.backend.crm.lead.entity.CrmLeadEnums;
import com.growthprint.backend.crm.lead.repository.CrmLeadRepository;
import com.growthprint.backend.invoice.entity.Invoice;
import com.growthprint.backend.invoice.entity.InvoiceCurrency;
import com.growthprint.backend.invoice.entity.InvoiceStatus;
import com.growthprint.backend.invoice.repository.InvoiceRepository;
import com.growthprint.backend.outreach.entity.OutreachDraft;
import com.growthprint.backend.outreach.entity.OutreachDraftStatus;
import com.growthprint.backend.outreach.repository.OutreachDraftRepository;
import com.growthprint.backend.project.entity.Milestone;
import com.growthprint.backend.project.entity.MilestoneStatus;
import com.growthprint.backend.project.entity.Project;
import com.growthprint.backend.project.entity.ProjectStatus;
import com.growthprint.backend.project.entity.ServiceCategory;
import com.growthprint.backend.project.repository.MilestoneRepository;
import com.growthprint.backend.project.repository.ProjectRepository;
import com.growthprint.backend.support.entity.BusinessImpact;
import com.growthprint.backend.support.entity.SupportCategory;
import com.growthprint.backend.support.entity.SupportRequest;
import com.growthprint.backend.support.entity.SupportRequestStatus;
import com.growthprint.backend.support.entity.Urgency;
import com.growthprint.backend.support.repository.SupportRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Standalone seed data command.
 * Can be invoked manually to seed development data.
 * Only runs when spring.profiles.active includes "seed".
 */
@Component
@Profile("seed")
public class SeedDataCommand implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SeedDataCommand.class);

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final CrmLeadRepository crmLeadRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final InvoiceRepository invoiceRepository;
    private final OutreachDraftRepository outreachDraftRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDataCommand(UserRepository userRepository,
                           ClientRepository clientRepository,
                           ProjectRepository projectRepository,
                           MilestoneRepository milestoneRepository,
                           CrmLeadRepository crmLeadRepository,
                           SupportRequestRepository supportRequestRepository,
                           InvoiceRepository invoiceRepository,
                           OutreachDraftRepository outreachDraftRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.crmLeadRepository = crmLeadRepository;
        this.supportRequestRepository = supportRequestRepository;
        this.invoiceRepository = invoiceRepository;
        this.outreachDraftRepository = outreachDraftRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedData();
    }

    @Transactional
    public void seedData() {
        logger.info("Starting seed data operation...");

        // Check if admin user already exists
        boolean adminExists = userRepository.findByNormalizedEmail("admin@growthprint.dev").isPresent();

        if (adminExists) {
            logger.info("Seed data already exists (admin user found). Skipping seed operation.");
            logger.info("Note: This seeder is designed for fresh databases. To reseed, clear existing data first.");
            return;
        }

        logger.info("Creating seed data...");

        // Create clients
        Client clientA = createClientA();
        Client clientB = createClientB();

        // Create users
        createAdminUser();
        createClientUser(clientA.getId(), "clienta@growthprint.dev", "ClientA123!");
        createClientUser(clientB.getId(), "clientb@growthprint.dev", "ClientB123!");

        // Create CRM leads
        CrmLead lead1 = createCrmLead1();
        CrmLead lead2 = createCrmLead2();
        CrmLead lead3 = createCrmLead3();

        // Create projects (one per client)
        Project projectA = createProject(clientA.getId(), "GP-PRJ-2026-001");
        Project projectB = createProject(clientB.getId(), "GP-PRJ-2026-002");

        // Create milestones for projects
        createMilestonesForProject(projectA.getId());
        createMilestonesForProject(projectB.getId());

        // Create support requests
        createSupportRequest(clientA.getId(), projectA.getId(), "GP-REQ-2026-001");
        createSupportRequest(clientA.getId(), null, "GP-REQ-2026-002");
        createSupportRequest(clientB.getId(), projectB.getId(), "GP-REQ-2026-003");

        // Create invoices
        createInvoice(clientA.getId(), "INV-2026-0041", InvoiceStatus.PAID);
        createInvoice(clientB.getId(), "INV-2026-0042", InvoiceStatus.ISSUED);

        // Create outreach drafts
        createOutreachDraft(lead1.getId());
        createOutreachDraft(lead2.getId());
        createOutreachDraft(lead3.getId());

        logger.info("Seed data completed successfully.");
        logger.info("Test accounts created:");
        logger.info("  - Admin: admin@growthprint.dev / Admin123!");
        logger.info("  - Client A: clienta@growthprint.dev / ClientA123! (Client ID: {})", clientA.getId());
        logger.info("  - Client B: clientb@growthprint.dev / ClientB123! (Client ID: {})", clientB.getId());
    }

    private Client createClientA() {
        Client client = new Client(
            UUID.randomUUID(),
            "TechStart Solutions",
            "TechStart Solutions LLC",
            "contact@techstart.example.com",
            "+1-555-0101",
            "https://techstart.example.com",
            ClientEnums.ClientStatus.ACTIVE,
            ClientEnums.RetainerTier.PRO_GROWTH,
            40,
            25,
            LocalDate.of(2025, 1, 15),
            "Sarah Johnson",
            ClientEnums.BillingCurrency.USD,
            "Early-stage SaaS company focused on developer tools"
        );
        return clientRepository.save(client);
    }

    private Client createClientB() {
        Client client = new Client(
            UUID.randomUUID(),
            "GreenLeaf Retail",
            "GreenLeaf Retail Group Inc.",
            "operations@greenleaf.example.com",
            "+1-555-0202",
            "https://greenleaf.example.com",
            ClientEnums.ClientStatus.ACTIVE,
            ClientEnums.RetainerTier.ESSENTIAL_MAINTENANCE,
            20,
            15,
            LocalDate.of(2025, 3, 1),
            "Michael Chen",
            ClientEnums.BillingCurrency.USD,
            "E-commerce platform for sustainable products"
        );
        return clientRepository.save(client);
    }

    private User createAdminUser() {
        User user = new User(
            UUID.randomUUID(),
            "admin@growthprint.dev",
            "admin@growthprint.dev",
            passwordEncoder.encode("Admin123!"),
            UserRole.ADMIN,
            AccountStatus.ACTIVE,
            null,
            Instant.now(),
            Instant.now()
        );
        return userRepository.save(user);
    }

    private User createClientUser(UUID clientId, String email, String password) {
        User user = new User(
            UUID.randomUUID(),
            email,
            email,
            passwordEncoder.encode(password),
            UserRole.CLIENT,
            AccountStatus.ACTIVE,
            clientId,
            Instant.now(),
            Instant.now()
        );
        return userRepository.save(user);
    }

    private CrmLead createCrmLead1() {
        CrmLead lead = new CrmLead(
            UUID.randomUUID(),
            "CloudScale Technologies",
            "https://cloudscale.example.com",
            "Cloud Infrastructure",
            "San Francisco, CA",
            "David Martinez",
            "david@cloudscale.example.com",
            "CTO",
            CrmLeadEnums.CrmLeadSource.TECH_AUDIT_DISCOVERY,
            "https://cloudscale.example.com/about",
            Instant.now().minusSeconds(86400 * 5),
            CrmLeadEnums.CrmLeadServiceCategory.AI_INTEGRATION,
            "Looking to integrate AI-powered analytics into their platform",
            "Company has $5M ARR, team of 20 engineers, actively seeking AI solutions",
            85,
            22,
            21,
            20,
            22,
            CrmLeadEnums.CrmLeadPipelineStage.QUALIFIED,
            null,
            "Schedule discovery call to discuss AI integration requirements",
            false,
            "High-priority lead, strong technical fit",
            null,
            Instant.now(),
            Instant.now()
        );
        return crmLeadRepository.save(lead);
    }

    private CrmLead createCrmLead2() {
        CrmLead lead = new CrmLead(
            UUID.randomUUID(),
            "DataFlow Analytics",
            "https://dataflow.example.com",
            "Data Analytics",
            "Austin, TX",
            "Emily Watson",
            "emily@dataflow.example.com",
            "VP Engineering",
            CrmLeadEnums.CrmLeadSource.INBOUND_INQUIRY,
            null,
            Instant.now().minusSeconds(86400 * 10),
            CrmLeadEnums.CrmLeadServiceCategory.SOFTWARE_DEV,
            "Legacy codebase needs modernization and performance optimization",
            "10-year-old monolithic application, experiencing scaling issues",
            72,
            18,
            18,
            18,
            18,
            CrmLeadEnums.CrmLeadPipelineStage.RESEARCHING,
            null,
            "Request technical audit and modernization proposal",
            false,
            "Interested in phased migration approach",
            null,
            Instant.now(),
            Instant.now()
        );
        return crmLeadRepository.save(lead);
    }

    private CrmLead createCrmLead3() {
        CrmLead lead = new CrmLead(
            UUID.randomUUID(),
            "MobileFirst Health",
            "https://mobilefirst.example.com",
            "Healthcare",
            "Boston, MA",
            "Robert Kim",
            "robert@mobilefirst.example.com",
            "Founder",
            CrmLeadEnums.CrmLeadSource.REFERRAL,
            null,
            Instant.now().minusSeconds(86400 * 15),
            CrmLeadEnums.CrmLeadServiceCategory.WEBSITE_MAINTENANCE,
            "Current website needs security updates and performance improvements",
            "Healthcare compliance requirements, needs HIPAA-compliant hosting",
            65,
            15,
            17,
            16,
            17,
            CrmLeadEnums.CrmLeadPipelineStage.NEEDS_REVIEW,
            null,
            "Review security requirements and prepare compliance checklist",
            false,
            "Referred by existing client TechStart Solutions",
            null,
            Instant.now(),
            Instant.now()
        );
        return crmLeadRepository.save(lead);
    }

    private Project createProject(UUID clientId, String reference) {
        Project project = new Project(
            UUID.randomUUID(),
            reference,
            "Platform Development Phase 1",
            clientId,
            ServiceCategory.SOFTWARE_DEV,
            "Core platform development including user authentication, dashboard, and API integration",
            ProjectStatus.IN_PROGRESS,
            45,
            LocalDate.of(2025, 11, 1),
            LocalDate.of(2026, 2, 28),
            new BigDecimal("45000.00"),
            new BigDecimal("20250.00"),
            "Alex Thompson",
            "[]",
            Instant.now(),
            Instant.now()
        );
        return projectRepository.save(project);
    }

    private void createMilestonesForProject(UUID projectId) {
        LocalDate projectStart = LocalDate.of(2025, 11, 1);

        Milestone milestone1 = new Milestone(
            UUID.randomUUID(),
            projectId,
            "Requirements Analysis & Design",
            projectStart.plusWeeks(2),
            MilestoneStatus.COMPLETED,
            Instant.now(),
            Instant.now()
        );
        milestoneRepository.save(milestone1);

        Milestone milestone2 = new Milestone(
            UUID.randomUUID(),
            projectId,
            "Core Development Sprint 1",
            projectStart.plusWeeks(6),
            MilestoneStatus.IN_PROGRESS,
            Instant.now(),
            Instant.now()
        );
        milestoneRepository.save(milestone2);

        Milestone milestone3 = new Milestone(
            UUID.randomUUID(),
            projectId,
            "Integration & Testing",
            projectStart.plusWeeks(10),
            MilestoneStatus.PENDING,
            Instant.now(),
            Instant.now()
        );
        milestoneRepository.save(milestone3);
    }

    private SupportRequest createSupportRequest(UUID clientId, UUID projectId, String reference) {
        SupportRequest request = new SupportRequest();
        request.setReference(reference);
        request.setClientId(clientId);
        request.setProjectId(projectId);
        request.setTitle("API integration issue with third-party service");
        request.setCategory(SupportCategory.INCLUDED_SUPPORT);
        request.setDescription("Experiencing intermittent failures when integrating with external payment API. Need investigation and fix.");
        request.setBusinessImpact(BusinessImpact.MEDIUM);
        request.setUrgency(Urgency.NORMAL);
        request.setStatus(SupportRequestStatus.IN_PROGRESS);
        request.setAdminNotes("Investigating API rate limits and error handling");
        request.setResolutionSummary(null);
        return supportRequestRepository.save(request);
    }

    private Invoice createInvoice(UUID clientId, String invoiceNumber, InvoiceStatus status) {
        Instant paidAt = status == InvoiceStatus.PAID ? Instant.now().minusSeconds(86400 * 7) : null;

        Invoice invoice = new Invoice(
            UUID.randomUUID(),
            invoiceNumber,
            clientId,
            status == InvoiceStatus.PAID ? "TechStart Solutions" : "GreenLeaf Retail",
            status == InvoiceStatus.PAID ? "contact@techstart.example.com" : "operations@greenleaf.example.com",
            LocalDate.now().minusDays(30),
            LocalDate.now().plusDays(30),
            new BigDecimal("5000.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("5000.00"),
            InvoiceCurrency.USD,
            status,
            paidAt,
            "Monthly retainer for development services",
            Instant.now(),
            Instant.now()
        );
        return invoiceRepository.save(invoice);
    }

    private OutreachDraft createOutreachDraft(UUID leadId) {
        OutreachDraft draft = new OutreachDraft(
            UUID.randomUUID(),
            leadId,
            "Sample Company",
            "Contact Name",
            "contact@example.com",
            "AI Integration Partnership Opportunity",
            "I noticed your company is exploring AI solutions. We'd love to discuss how our expertise could help accelerate your integration efforts.",
            "Recently published blog post about AI challenges in SaaS",
            ServiceCategory.AI_INTEGRATION,
            "[]",
            OutreachDraftStatus.DRAFT,
            false,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
        return outreachDraftRepository.save(draft);
    }
}
