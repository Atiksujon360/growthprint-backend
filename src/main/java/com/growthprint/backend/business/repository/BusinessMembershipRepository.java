package com.growthprint.backend.business.repository;

import com.growthprint.backend.business.entity.BusinessMembership;
import com.growthprint.backend.business.entity.BusinessRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessMembershipRepository extends JpaRepository<BusinessMembership, BusinessMembership.Key> {

    @Query("select m from BusinessMembership m where m.id.businessId = :businessId and m.id.userId = :userId")
    Optional<BusinessMembership> findOne(UUID businessId, UUID userId);

    @Query("select m from BusinessMembership m where m.id.businessId = :businessId and m.id.userId = :userId "
            + "and m.status = 'ACTIVE'")
    Optional<BusinessMembership> findActive(UUID businessId, UUID userId);

    @Query("select m from BusinessMembership m where m.id.userId = :userId and m.status = 'ACTIVE'")
    List<BusinessMembership> findActiveByUser(UUID userId);

    @Query("select m from BusinessMembership m where m.id.businessId = :businessId and m.status = 'ACTIVE' "
            + "order by m.createdAt")
    List<BusinessMembership> findActiveByBusiness(UUID businessId);

    /**
     * Active members with a role whose user account is also ACTIVE. A suspended or invited account does not count
     * as an owner for last-owner protection, so a business can never be left with only unusable owners.
     */
    @Query("select count(m) from BusinessMembership m, com.growthprint.backend.auth.entity.User u "
            + "where u.id = m.id.userId and m.id.businessId = :businessId and m.role = :role "
            + "and m.status = 'ACTIVE' and u.accountStatus = com.growthprint.backend.auth.entity.AccountStatus.ACTIVE")
    long countActiveWithRole(UUID businessId, BusinessRole role);
}
