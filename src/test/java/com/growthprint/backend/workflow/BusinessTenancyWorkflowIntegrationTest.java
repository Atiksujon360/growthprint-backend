package com.growthprint.backend.workflow;

import com.growthprint.backend.business.entity.Business;
import com.growthprint.backend.testsupport.AbstractPostgresIntegrationTest;
import com.growthprint.backend.testsupport.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * M1 over HTTP against real PostgreSQL (V21): the default business exists, users can belong to several businesses
 * with independent roles, and a member of one business cannot see or change another.
 */
@PostgresIntegrationTest
class BusinessTenancyWorkflowIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Tenant-Test-Pass-1";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private String alice;
    private String bob;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void users() throws Exception {
        alice = createUser("alice");
        bob = createUser("bob");
        aliceToken = login(alice);
        bobToken = login(bob);
    }

    @Test
    void defaultGrowthPrintBusinessExists() {
        assertThat(jdbc.queryForObject("SELECT slug FROM businesses WHERE id = ?::uuid", String.class,
                Business.DEFAULT_BUSINESS_ID.toString())).isEqualTo("growthprint");
    }

    @Test
    void membersOfOneBusinessCannotSeeOrChangeAnother() throws Exception {
        String acme = JsonPath.read(body(call(HttpMethod.POST, "/api/v1/businesses", aliceToken,
                "{\"name\":\"Acme Cleaning\"}", 201)), "$.id");
        String globex = JsonPath.read(body(call(HttpMethod.POST, "/api/v1/businesses", bobToken,
                "{\"name\":\"Globex Consulting\"}", 201)), "$.id");

        // Each sees only their own business
        List<String> aliceBusinesses = JsonPath.read(body(call(HttpMethod.GET, "/api/v1/businesses", aliceToken, null, 200)), "$[*].id");
        assertThat(aliceBusinesses).contains(acme).doesNotContain(globex);

        // Cross-business access is 404 (indistinguishable from a missing business) and changes nothing
        call(HttpMethod.GET, "/api/v1/businesses/" + globex, aliceToken, null, 404);
        call(HttpMethod.GET, "/api/v1/businesses/" + globex + "/members", aliceToken, null, 404);
        call(HttpMethod.PATCH, "/api/v1/businesses/" + globex, aliceToken, "{\"name\":\"Hacked\"}", 404);
        call(HttpMethod.POST, "/api/v1/businesses/" + globex + "/invitations", aliceToken,
                "{\"email\":\"x@example.test\",\"role\":\"MEMBER\"}", 404);
        assertThat(jdbc.queryForObject("SELECT name FROM businesses WHERE id = ?::uuid", String.class, globex))
                .isEqualTo("Globex Consulting");
    }

    @Test
    void oneUserCanHoldDifferentRolesInDifferentBusinesses() throws Exception {
        String acme = JsonPath.read(body(call(HttpMethod.POST, "/api/v1/businesses", aliceToken,
                "{\"name\":\"Acme Two\"}", 201)), "$.id");
        String globex = JsonPath.read(body(call(HttpMethod.POST, "/api/v1/businesses", bobToken,
                "{\"name\":\"Globex Two\"}", 201)), "$.id");

        // Bob invites Alice into Globex as MANAGER; Alice accepts with her own account
        String token = JsonPath.read(body(call(HttpMethod.POST, "/api/v1/businesses/" + globex + "/invitations",
                bobToken, "{\"email\":\"" + alice + "\",\"role\":\"MANAGER\"}", 201)), "$.token");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_invitations WHERE token_hash = ?",
                Integer.class, token)).isZero();
        call(HttpMethod.POST, "/api/v1/business-invitations/accept", aliceToken, "{\"token\":\"" + token + "\"}", 200);
        call(HttpMethod.POST, "/api/v1/business-invitations/accept", aliceToken, "{\"token\":\"" + token + "\"}", 400);

        MvcResult mine = call(HttpMethod.GET, "/api/v1/businesses", aliceToken, null, 200);
        List<String> roleInAcme = JsonPath.read(body(mine), "$[?(@.id == '" + acme + "')].myRole");
        List<String> roleInGlobex = JsonPath.read(body(mine), "$[?(@.id == '" + globex + "')].myRole");
        assertThat(roleInAcme).containsExactly("OWNER");
        assertThat(roleInGlobex).containsExactly("MANAGER");

        // As a MANAGER in Globex, Alice cannot rename it or invite people
        call(HttpMethod.PATCH, "/api/v1/businesses/" + globex, aliceToken, "{\"name\":\"Renamed\"}", 403);
        call(HttpMethod.POST, "/api/v1/businesses/" + globex + "/invitations", aliceToken,
                "{\"email\":\"y@example.test\",\"role\":\"MEMBER\"}", 403);

        // Bob is the only owner: he cannot leave until ownership is transferred
        String bobId = jdbc.queryForObject("SELECT id::text FROM users WHERE normalized_email = ?", String.class, bob);
        String aliceId = jdbc.queryForObject("SELECT id::text FROM users WHERE normalized_email = ?", String.class, alice);
        call(HttpMethod.DELETE, "/api/v1/businesses/" + globex + "/members/" + bobId, bobToken, null, 409);
        call(HttpMethod.POST, "/api/v1/businesses/" + globex + "/ownership-transfer", aliceToken,
                "{\"userId\":\"" + aliceId + "\"}", 403);
        call(HttpMethod.POST, "/api/v1/businesses/" + globex + "/ownership-transfer", bobToken,
                "{\"userId\":\"" + aliceId + "\"}", 200);
        call(HttpMethod.DELETE, "/api/v1/businesses/" + globex + "/members/" + bobId, bobToken, null, 204);
        call(HttpMethod.GET, "/api/v1/businesses/" + globex, bobToken, null, 404);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private String createUser(String name) {
        String email = name + "+" + UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO users (id, email, normalized_email, password_hash, role, account_status, full_name) "
                        + "VALUES (?, ?, ?, ?, 'ADMIN', 'ACTIVE', ?)",
                UUID.randomUUID(), email, email, passwordEncoder.encode(PASSWORD), name);
        return email;
    }

    private String login(String email) throws Exception {
        return JsonPath.read(body(call(HttpMethod.POST, "/api/v1/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", 200)), "$.accessToken");
    }

    private MvcResult call(HttpMethod method, String path, String token, String json, int expectedStatus)
            throws Exception {
        var builder = request(method, path);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (json != null) builder.contentType(MediaType.APPLICATION_JSON).content(json);
        MvcResult result = mvc.perform(builder).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("%s %s -> %s", method, path, result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return result;
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
