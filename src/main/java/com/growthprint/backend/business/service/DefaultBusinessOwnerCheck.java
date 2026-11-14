package com.growthprint.backend.business.service;

import com.growthprint.backend.business.entity.Business;
import com.growthprint.backend.business.entity.BusinessRole;
import com.growthprint.backend.business.repository.BusinessMembershipRepository;
import com.growthprint.backend.business.repository.BusinessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup check for the V22 "no eligible owner" case: warns when the default GrowthPrint business has no OWNER
 * with an ACTIVE account. Read-only: it never creates or changes memberships (owner assignment follows the
 * documented recovery procedure).
 */
@Component
public class DefaultBusinessOwnerCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultBusinessOwnerCheck.class);

    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;

    public DefaultBusinessOwnerCheck(BusinessRepository businessRepository,
                                     BusinessMembershipRepository membershipRepository) {
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (businessRepository.existsById(Business.DEFAULT_BUSINESS_ID)
                    && membershipRepository.countActiveWithRole(Business.DEFAULT_BUSINESS_ID, BusinessRole.OWNER) == 0) {
                log.warn("The default GrowthPrint business has no OWNER with an active account. Business "
                        + "management for it is unavailable until an owner is assigned (see "
                        + "docs/architecture/MVP2-M1-1-SECURITY-HARDENING.md, 'Ownerless default business').");
            }
        } catch (RuntimeException e) {
            log.warn("Could not verify default business ownership: {}", e.getClass().getSimpleName());
        }
    }
}
