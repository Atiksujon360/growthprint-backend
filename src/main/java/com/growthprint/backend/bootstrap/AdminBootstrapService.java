package com.growthprint.backend.bootstrap;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.auth.service.PasswordPolicy;
import com.growthprint.backend.business.entity.Business;
import com.growthprint.backend.business.entity.BusinessMembership;
import com.growthprint.backend.business.entity.BusinessRole;
import com.growthprint.backend.business.repository.BusinessMembershipRepository;
import com.growthprint.backend.business.repository.BusinessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * First-admin bootstrap (production hardening). There is no seeded account with a known password: on a database
 * with no ACTIVE admin, the operator supplies BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD for one start-up,
 * signs in, and removes the password variable.
 *
 * <p>Rules: does nothing unless both values are set; never changes an existing account; the password must satisfy
 * {@link PasswordPolicy}; the password is never logged. When a bootstrap admin is configured it also restores the
 * OWNER role on the default business if that business has no active owner (for example after a reset).
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    public enum Outcome { NOT_CONFIGURED, CREATED, ALREADY_EXISTS, OWNER_RESTORED }

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(UserRepository userRepository, BusinessRepository businessRepository,
                                 BusinessMembershipRepository membershipRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Outcome bootstrap(String email, String password) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        if (!hasEmail && !hasPassword) {
            return Outcome.NOT_CONFIGURED;
        }
        if (hasEmail != hasPassword) {
            throw new IllegalStateException(
                    "Set both BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD, or neither.");
        }

        String cleanEmail = email.trim();
        String normalized = cleanEmail.toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || normalized.length() > 254) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL is not a valid email address.");
        }

        User existing = userRepository.findByNormalizedEmail(normalized).orElse(null);
        boolean noActiveAdmin = userRepository.countByRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE) == 0;

        User admin = existing;
        Outcome outcome = Outcome.ALREADY_EXISTS;
        if (noActiveAdmin && existing == null) {
            List<String> problems = PasswordPolicy.violations(password, cleanEmail);
            if (!problems.isEmpty()) {
                // Reasons only; the password itself is never echoed.
                throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD is not acceptable: " + String.join("; ", problems));
            }
            Instant now = Instant.now();
            admin = userRepository.save(new User(UUID.randomUUID(), cleanEmail, normalized,
                    passwordEncoder.encode(password), UserRole.ADMIN, AccountStatus.ACTIVE, null, now, now));
            outcome = Outcome.CREATED;
            log.info("Bootstrap admin account created. Remove BOOTSTRAP_ADMIN_PASSWORD from the environment now.");
        } else if (noActiveAdmin) {
            // An account with this email exists but no admin is active: never change an existing password silently.
            log.warn("No active admin exists, but the configured email is already registered. Bootstrap does not "
                    + "modify existing accounts; activate or recover that account instead.");
            return Outcome.ALREADY_EXISTS;
        }

        boolean restored = restoreDefaultBusinessOwner(admin);
        return restored && outcome == Outcome.ALREADY_EXISTS ? Outcome.OWNER_RESTORED : outcome;
    }

    /** Gives the configured admin the OWNER role on the default business when it has no usable owner. */
    private boolean restoreDefaultBusinessOwner(User admin) {
        if (admin == null || admin.getRole() != UserRole.ADMIN || admin.getAccountStatus() != AccountStatus.ACTIVE) {
            return false;
        }
        UUID businessId = Business.DEFAULT_BUSINESS_ID;
        if (!businessRepository.existsById(businessId)
                || membershipRepository.countActiveWithRole(businessId, BusinessRole.OWNER) > 0) {
            return false;
        }
        Instant now = Instant.now();
        BusinessMembership membership = membershipRepository.findOne(businessId, admin.getId()).orElse(null);
        if (membership == null) {
            membershipRepository.save(new BusinessMembership(businessId, admin.getId(), BusinessRole.OWNER, null, now));
        } else {
            membership.reactivate(BusinessRole.OWNER, null);
            membershipRepository.save(membership);
        }
        log.info("Default business owner restored to the configured admin.");
        return true;
    }
}
