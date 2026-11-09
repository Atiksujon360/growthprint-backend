package com.growthprint.backend.business.service;

import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.exception.UserNotFoundException;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.business.dto.BusinessDtos.BusinessResponse;
import com.growthprint.backend.business.dto.BusinessDtos.CreateBusinessRequest;
import com.growthprint.backend.business.entity.Business;
import com.growthprint.backend.business.entity.BusinessMembership;
import com.growthprint.backend.business.entity.BusinessRole;
import com.growthprint.backend.business.exception.BusinessExceptions.BusinessAccessDeniedException;
import com.growthprint.backend.business.repository.BusinessMembershipRepository;
import com.growthprint.backend.business.repository.BusinessRepository;
import com.growthprint.backend.business.service.BusinessAccessService.BusinessContext;
import com.growthprint.backend.security.SecurityAudit;
import com.growthprint.backend.security.SecurityAudit.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Creating, listing, viewing and renaming businesses. */
@Service
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final BusinessAccessService access;
    private final Clock clock;
    private final boolean selfServiceCreationEnabled;

    public BusinessService(BusinessRepository businessRepository, BusinessMembershipRepository membershipRepository,
                           UserRepository userRepository, BusinessAccessService access, Clock clock,
                           @Value("${app.businesses.self-service-creation-enabled:false}")
                           boolean selfServiceCreationEnabled) {
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.clock = clock;
        this.selfServiceCreationEnabled = selfServiceCreationEnabled;
    }

    /** Businesses the caller is an active member of, with their role in each (for the business switcher). */
    @Transactional(readOnly = true)
    public List<BusinessResponse> listMine(UUID userId) {
        return membershipRepository.findActiveByUser(userId).stream()
                .map(m -> businessRepository.findById(m.getBusinessId())
                        .filter(Business::isActive)
                        .map(b -> toResponse(b, m.getRole()))
                        .orElse(null))
                .filter(r -> r != null)
                .sorted(Comparator.comparing(BusinessResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Creates a business; the creator becomes its OWNER.
     * <p>
     * Until tenant migration (M2) scopes MVP1 data, creation is restricted to ACTIVE global ADMINs
     * ({@code app.businesses.self-service-creation-enabled=false}, the default). Business roles do not grant
     * access to MVP1 data, but restricting creation keeps the platform closed to new tenants until their data
     * can actually be isolated. Client-portal accounts can never create businesses.
     */
    @Transactional
    public BusinessResponse create(UUID userId, CreateBusinessRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        if (user.getRole() == UserRole.CLIENT) {
            throw new BusinessAccessDeniedException("Client portal accounts cannot create businesses");
        }
        if (!selfServiceCreationEnabled && user.getRole() != UserRole.ADMIN) {
            throw new BusinessAccessDeniedException(
                    "Creating businesses is limited to administrators until tenant data migration is complete");
        }
        String name = request.name().trim();
        String slug = request.slug() == null || request.slug().isBlank()
                ? uniqueSlug(slugify(name))
                : request.slug().trim();
        if (businessRepository.existsBySlug(slug)) {
            throw new IllegalStateException("That business address is already taken");
        }
        Instant now = clock.instant();
        Business business = businessRepository.save(new Business(UUID.randomUUID(), name, slug, userId, now));
        membershipRepository.save(new BusinessMembership(business.getId(), userId, BusinessRole.OWNER, null, now));
        SecurityAudit.record(Event.BUSINESS_CREATED, userId, userId, "business=" + business.getId());
        return toResponse(business, BusinessRole.OWNER);
    }

    @Transactional(readOnly = true)
    public BusinessResponse get(UUID businessId, UUID userId) {
        BusinessContext ctx = access.require(businessId, userId, BusinessRole.MEMBER);
        return toResponse(businessRepository.findById(businessId).orElseThrow(), ctx.role());
    }

    @Transactional
    public BusinessResponse rename(UUID businessId, UUID userId, String newName) {
        BusinessContext ctx = access.require(businessId, userId, BusinessRole.ADMIN);
        Business business = businessRepository.findById(businessId).orElseThrow();
        business.rename(newName.trim());
        SecurityAudit.record(Event.BUSINESS_UPDATED, userId, null, "business=" + businessId);
        return toResponse(business, ctx.role());
    }

    static String slugify(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 70) {
            slug = slug.substring(0, 70).replaceAll("-+$", "");
        }
        return slug.length() < 2 ? "business" : slug;
    }

    private String uniqueSlug(String base) {
        if (!businessRepository.existsBySlug(base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "-" + i;
            if (!businessRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static BusinessResponse toResponse(Business b, BusinessRole role) {
        return new BusinessResponse(b.getId(), b.getName(), b.getSlug(), b.getStatus(), role.name(), b.getCreatedAt());
    }
}
