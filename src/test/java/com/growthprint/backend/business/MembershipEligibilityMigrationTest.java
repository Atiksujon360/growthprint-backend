package com.growthprint.backend.business;

import com.growthprint.backend.testsupport.PostgresAvailableCondition;
import com.growthprint.backend.testsupport.PostgresTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the real migrations against PostgreSQL in a private schema: V1–V20, then MVP1-shaped users in every
 * account state, then V21–V23. Verifies the V21 backfill as corrected by V22:
 * <ul>
 *     <li>ACTIVE global ADMIN → OWNER of the default business;</li>
 *     <li>INVITED / SUSPENDED global ADMIN → MEMBER (not OWNER);</li>
 *     <li>TEAM_MEMBER → MEMBER; DISABLED users and CLIENT portal accounts → no membership.</li>
 * </ul>
 */
@Tag("postgres")
@ExtendWith(PostgresAvailableCondition.class)
class MembershipEligibilityMigrationTest {

    private static final String DEFAULT_BUSINESS = "6a1e0b7c-0000-4000-8000-00000000b001";

    private String baseUrl;
    private String schema;

    @BeforeEach
    void createSchema() throws Exception {
        baseUrl = System.getenv(PostgresTestDatabase.URL_ENV);
        if (baseUrl.toLowerCase(Locale.ROOT).contains("render.com")) {
            throw new IllegalStateException("Refusing to run migration tests against a Render host");
        }
        schema = "gp_mig_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = connect(baseUrl); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
        }
    }

    @AfterEach
    void dropSchema() throws Exception {
        try (Connection c = connect(baseUrl); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void v21AndV22MapOnlyActiveAdminsToOwner() throws Exception {
        flyway("20").migrate();

        Map<String, UUID> users = new HashMap<>();
        try (Connection c = connect(schemaUrl())) {
            UUID clientId = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO clients (id, name, legal_name, contact_email, "
                    + "website, status, contract_start_date, account_manager, billing_currency) "
                    + "VALUES (?, 'Acme', 'Acme Ltd', 'ops@acme.test', 'https://acme.test', 'ACTIVE', CURRENT_DATE, "
                    + "'GrowthPrint', 'USD')")) {
                ps.setObject(1, clientId);
                ps.executeUpdate();
            }
            users.put("activeAdmin", insertUser(c, "ADMIN", "ACTIVE", null, "hash"));
            users.put("invitedAdmin", insertUser(c, "ADMIN", "INVITED", null, null));
            users.put("suspendedAdmin", insertUser(c, "ADMIN", "SUSPENDED", null, "hash"));
            users.put("disabledAdmin", insertUser(c, "ADMIN", "DISABLED", null, "hash"));
            users.put("teamMember", insertUser(c, "TEAM_MEMBER", "ACTIVE", null, "hash"));
            users.put("client", insertUser(c, "CLIENT", "ACTIVE", clientId, "hash"));
        }

        flyway(null).migrate();

        try (Connection c = connect(schemaUrl())) {
            assertThat(scalar(c, "SELECT slug FROM businesses WHERE id = '" + DEFAULT_BUSINESS + "'")).isEqualTo("growthprint");
            assertThat(roleOf(c, users.get("activeAdmin"))).isEqualTo("OWNER");
            assertThat(roleOf(c, users.get("invitedAdmin"))).isEqualTo("MEMBER");
            assertThat(roleOf(c, users.get("suspendedAdmin"))).isEqualTo("MEMBER");
            assertThat(roleOf(c, users.get("disabledAdmin"))).isNull();
            assertThat(roleOf(c, users.get("teamMember"))).isEqualTo("MEMBER");
            assertThat(roleOf(c, users.get("client"))).as("client portal accounts are customers, not members").isNull();
            // V23 columns exist and are empty for existing leads
            assertThat(scalar(c, "SELECT COUNT(*) FROM crm_leads WHERE converted_client_id IS NOT NULL")).isEqualTo("0");
        }
    }

    @Test
    void withNoActiveAdminTheDefaultBusinessHasNoOwner() throws Exception {
        flyway("20").migrate();
        UUID suspended;
        try (Connection c = connect(schemaUrl())) {
            suspended = insertUser(c, "ADMIN", "SUSPENDED", null, "hash");
        }
        flyway(null).migrate();
        try (Connection c = connect(schemaUrl())) {
            assertThat(roleOf(c, suspended)).isEqualTo("MEMBER");
            assertThat(scalar(c, "SELECT COUNT(*) FROM business_memberships WHERE business_id = '" + DEFAULT_BUSINESS
                    + "' AND role = 'OWNER'")).isEqualTo("0");
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(baseUrl, System.getenv(PostgresTestDatabase.USERNAME_ENV),
                        System.getenv(PostgresTestDatabase.PASSWORD_ENV))
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) {
            config = config.target(target);
        }
        return config.load();
    }

    private String schemaUrl() {
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private static Connection connect(String url) throws Exception {
        return DriverManager.getConnection(url, System.getenv(PostgresTestDatabase.USERNAME_ENV),
                System.getenv(PostgresTestDatabase.PASSWORD_ENV));
    }

    private static UUID insertUser(Connection c, String role, String status, UUID clientId, String hash) throws Exception {
        UUID id = UUID.randomUUID();
        String email = role.toLowerCase(Locale.ROOT) + "+" + id + "@example.test";
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (id, email, normalized_email, password_hash, "
                + "role, account_status, client_id, client_role) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, email);
            ps.setString(3, email);
            ps.setString(4, hash);
            ps.setString(5, role);
            ps.setString(6, status);
            ps.setObject(7, clientId);
            ps.setString(8, clientId == null ? null : "CLIENT_OWNER");
            ps.executeUpdate();
        }
        return id;
    }

    private static String roleOf(Connection c, UUID userId) throws Exception {
        return scalar(c, "SELECT role FROM business_memberships WHERE business_id = '" + DEFAULT_BUSINESS
                + "' AND user_id = '" + userId + "'");
    }

    private static String scalar(Connection c, String sql) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
