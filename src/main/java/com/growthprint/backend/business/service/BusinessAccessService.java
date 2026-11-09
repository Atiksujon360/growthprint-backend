package com.growthprint.backend.business.service;

import com.growthprint.backend.business.entity.BusinessMembership;
import com.growthprint.backend.business.entity.BusinessRole;
import com.growthprint.backend.business.exception.BusinessExceptions.BusinessAccessDeniedException;
import com.growthprint.backend.business.exception.BusinessExceptions.BusinessNotFoundException;
import com.growthprint.backend.business.repository.BusinessMembershipRepository;
import com.growthprint.backend.business.repository.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single place that turns "authenticated user + business id from the request" into an authorized business
 * context. A business id sent by the browser is never trusted on its own: the caller must hold an ACTIVE
 * membership in an ACTIVE business, otherwise the answer is 404 (indistinguishable from a missing business).
 * Every tenant-scoped service must call {@link #require} before touching business-owned data.
 */
@Service
public class BusinessAccessService {

    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;

    public BusinessAccessService(BusinessRepository businessRepository,
                                 BusinessMembershipRepository membershipRepository) {
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
    }

    /** Resolved, verified business context for one request. */
    public record BusinessContext(UUID businessId, UUID userId, BusinessRole role) {
    }

    @Transactional(readOnly = true)
    public BusinessContext require(UUID businessId, UUID userId, BusinessRole minimumRole) {
        if (businessId == null || userId == null) {
            throw new BusinessNotFoundException();
        }
        boolean businessActive = businessRepository.findById(businessId).map(b -> b.isActive()).orElse(false);
        if (!businessActive) {
            throw new BusinessNotFoundException();
        }
        BusinessMembership membership = membershipRepository.findActive(businessId, userId)
                .orElseThrow(BusinessNotFoundException::new);
        if (!membership.getRole().atLeast(minimumRole)) {
            throw new BusinessAccessDeniedException("This action requires the " + minimumRole.name()
                    + " role or higher in this business");
        }
        return new BusinessContext(businessId, userId, membership.getRole());
    }
}
