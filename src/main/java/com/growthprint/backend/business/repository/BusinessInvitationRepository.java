package com.growthprint.backend.business.repository;

import com.growthprint.backend.business.entity.BusinessInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessInvitationRepository extends JpaRepository<BusinessInvitation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from BusinessInvitation i where i.tokenHash = :tokenHash")
    Optional<BusinessInvitation> findForUpdateByTokenHash(String tokenHash);

    @Query("select i from BusinessInvitation i where i.businessId = :businessId and i.status = 'PENDING' "
            + "order by i.createdAt desc")
    List<BusinessInvitation> findPendingByBusiness(UUID businessId);

    @Query("select i from BusinessInvitation i where i.businessId = :businessId "
            + "and i.normalizedEmail = :email and i.status = 'PENDING'")
    List<BusinessInvitation> findPending(UUID businessId, String email);

    /** Scoped lookup: an invitation id from another business is simply "not found". */
    Optional<BusinessInvitation> findByIdAndBusinessId(UUID id, UUID businessId);
}
