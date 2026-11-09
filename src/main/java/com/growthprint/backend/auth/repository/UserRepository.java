package com.growthprint.backend.auth.repository;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByNormalizedEmail(String normalizedEmail);

    Optional<User> findByNormalizedEmailAndAccountStatus(String normalizedEmail, AccountStatus accountStatus);

    boolean existsByNormalizedEmail(String normalizedEmail);

    /** Loads the user with module permissions in one query (used on every authenticated request). */
    @EntityGraph(attributePaths = "permissions")
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithPermissionsById(UUID id);

    /** Row lock for security-sensitive updates (password, status, role) to avoid lost updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdateById(UUID id);

    @EntityGraph(attributePaths = "permissions")
    @Query("select u from User u order by u.createdAt desc")
    List<User> findAllWithPermissions();

    long countByRoleAndAccountStatus(UserRole role, AccountStatus accountStatus);
}
