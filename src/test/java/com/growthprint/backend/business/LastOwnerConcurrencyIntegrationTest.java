package com.growthprint.backend.business;

import com.growthprint.backend.business.entity.BusinessRole;
import com.growthprint.backend.business.service.BusinessMembershipService;
import com.growthprint.backend.testsupport.AbstractPostgresIntegrationTest;
import com.growthprint.backend.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two owners demote themselves at the same moment. Without the business row lock both would see "2 owners" and
 * succeed, leaving the business ownerless. With it, exactly one succeeds and one OWNER remains.
 */
@PostgresIntegrationTest
class LastOwnerConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private BusinessMembershipService membershipService;
    @Autowired private JdbcTemplate jdbc;

    @RepeatedTest(5)
    void concurrentSelfDemotionsNeverRemoveTheLastOwner() throws Exception {
        UUID business = UUID.randomUUID();
        jdbc.update("INSERT INTO businesses (id, name, slug, status, created_at, updated_at) "
                + "VALUES (?, 'Race Co', ?, 'ACTIVE', now(), now())", business, "race-" + business.toString().substring(0, 8));
        UUID ownerA = owner(business);
        UUID ownerB = owner(business);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = new ArrayList<>();
        for (UUID owner : List.of(ownerA, ownerB)) {
            Callable<Boolean> demoteSelf = () -> {
                start.await();
                try {
                    membershipService.changeRole(business, owner, owner, BusinessRole.ADMIN);
                    return true;
                } catch (IllegalStateException lastOwner) {
                    return false;
                }
            };
            results.add(pool.submit(demoteSelf));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) succeeded++;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_memberships WHERE business_id = ? "
                + "AND role = 'OWNER' AND status = 'ACTIVE'", Integer.class, business)).isEqualTo(1);
    }

    private UUID owner(UUID business) {
        UUID id = UUID.randomUUID();
        String email = "owner+" + id + "@example.test";
        jdbc.update("INSERT INTO users (id, email, normalized_email, password_hash, role, account_status) "
                + "VALUES (?, ?, ?, 'hash', 'ADMIN', 'ACTIVE')", id, email, email);
        jdbc.update("INSERT INTO business_memberships (business_id, user_id, role, status, created_at, updated_at) "
                + "VALUES (?, ?, 'OWNER', 'ACTIVE', now(), now())", business, id);
        return id;
    }
}
