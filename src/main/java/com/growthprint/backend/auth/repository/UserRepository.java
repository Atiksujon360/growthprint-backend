package com.growthprint.backend.auth.repository;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByNormalizedEmail(String normalizedEmail);

    Optional<User> findByNormalizedEmailAndAccountStatus(String normalizedEmail, AccountStatus accountStatus);

    boolean existsByNormalizedEmail(String normalizedEmail);
}
