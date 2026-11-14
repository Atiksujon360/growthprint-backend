package com.growthprint.backend.business.repository;

import com.growthprint.backend.business.entity.Business;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessRepository extends JpaRepository<Business, UUID> {

    boolean existsBySlug(String slug);

    /**
     * Row lock on the business. Every change to the set of owners takes this lock first, so two concurrent
     * demotions/removals cannot both pass the "at least one owner" check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Business b where b.id = :id")
    Optional<Business> findForUpdateById(UUID id);
}
