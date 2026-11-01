package com.growthprint.backend.auth.repository;

import com.growthprint.backend.auth.entity.UserInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

    Optional<UserInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UserInvitation i where i.tokenHash = :tokenHash")
    Optional<UserInvitation> findForUpdateByTokenHash(String tokenHash);

    List<UserInvitation> findByUserIdAndStatus(UUID userId, String status);
}
