package com.growthprint.backend.business.service;

import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.exception.InvalidAccountTokenException;
import com.growthprint.backend.auth.exception.UserNotFoundException;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.business.dto.BusinessDtos.BusinessResponse;
import com.growthprint.backend.business.dto.BusinessDtos.CreatedInvitationResponse;
import com.growthprint.backend.business.dto.BusinessDtos.InvitationResponse;
import com.growthprint.backend.business.dto.BusinessDtos.MemberResponse;
import com.growthprint.backend.business.entity.Business;
import com.growthprint.backend.business.entity.BusinessInvitation;
import com.growthprint.backend.business.entity.BusinessMembership;
import com.growthprint.backend.business.entity.BusinessRole;
import com.growthprint.backend.business.exception.BusinessExceptions.BusinessAccessDeniedException;
import com.growthprint.backend.business.exception.BusinessExceptions.BusinessNotFoundException;
import com.growthprint.backend.business.repository.BusinessInvitationRepository;
import com.growthprint.backend.business.repository.BusinessMembershipRepository;
import com.growthprint.backend.business.repository.BusinessRepository;
import com.growthprint.backend.business.service.BusinessAccessService.BusinessContext;
import com.growthprint.backend.config.AccountSecurityProperties;
import com.growthprint.backend.security.SecureTokens;
import com.growthprint.backend.security.SecurityAudit;
import com.growthprint.backend.security.SecurityAudit.Event;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Membership rules for a business:
 * <ul>
 *     <li>ADMIN or higher manages members; only an OWNER can grant, remove or demote the OWNER role.</li>
 *     <li>A member can only change or remove members ranked below them (an OWNER can act on anyone).</li>
 *     <li>A business always keeps at least one active OWNER (checked under a row lock on the business).</li>
 *     <li>Ownership transfer is performed only by a current OWNER; the previous owner becomes ADMIN.</li>
 *     <li>Anyone can leave a business, except its last OWNER.</li>
 *     <li>Invitations are single-use, expire, are bound to an email address, and store only a token hash.</li>
 * </ul>
 * Every change is written to the SECURITY_AUDIT log (persistent audit history follows in milestone M5).
 */
@Service
public class BusinessMembershipService {

    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final BusinessInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final BusinessAccessService access;
    private final AccountSecurityProperties properties;
    private final Clock clock;

    public BusinessMembershipService(BusinessRepository businessRepository,
                                     BusinessMembershipRepository membershipRepository,
                                     BusinessInvitationRepository invitationRepository,
                                     UserRepository userRepository, BusinessAccessService access,
                                     AccountSecurityProperties properties, Clock clock) {
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.properties = properties;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------------------------------
    // Members
    // ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID businessId, UUID actorId) {
        access.require(businessId, actorId, BusinessRole.MEMBER);
        List<BusinessMembership> members = membershipRepository.findActiveByBusiness(businessId);
        Map<UUID, User> users = userRepository.findAllById(members.stream().map(BusinessMembership::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return members.stream()
                .map(m -> {
                    User u = users.get(m.getUserId());
                    return new MemberResponse(m.getUserId(), u == null ? null : u.getEmail(),
                            u == null ? null : u.getFullName(), m.getRole().name(), m.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public MemberResponse changeRole(UUID businessId, UUID actorId, UUID targetUserId, BusinessRole newRole) {
        // Lock first, then authorize: the actor's role is read under the same lock every role change takes,
        // so a concurrent demotion of the actor cannot let a stale, higher role through.
        lockBusiness(businessId);
        BusinessContext actor = access.require(businessId, actorId, BusinessRole.ADMIN);
        BusinessMembership target = membershipRepository.findActive(businessId, targetUserId)
                .orElseThrow(BusinessNotFoundException::new);
        BusinessRole previous = target.getRole();
        if (previous == newRole) {
            return toMember(target);
        }
        boolean ownerInvolved = previous == BusinessRole.OWNER || newRole == BusinessRole.OWNER;
        if (ownerInvolved && actor.role() != BusinessRole.OWNER) {
            throw new BusinessAccessDeniedException("Only an owner can grant or remove the owner role");
        }
        if (actor.role() != BusinessRole.OWNER) {
            if (!actor.role().outranks(previous) || !actor.role().outranks(newRole)) {
                throw new BusinessAccessDeniedException("You can only manage roles below your own");
            }
        }
        if (previous == BusinessRole.OWNER) {
            ensureAnotherOwner(businessId);
        }
        target.changeRole(newRole);
        SecurityAudit.record(Event.MEMBERSHIP_ROLE_CHANGED, actorId, targetUserId,
                "business=" + businessId + " " + previous + "->" + newRole);
        return toMember(target);
    }

    @Transactional
    public void removeMember(UUID businessId, UUID actorId, UUID targetUserId) {
        boolean leaving = actorId.equals(targetUserId);
        lockBusiness(businessId);
        BusinessContext actor = access.require(businessId, actorId, leaving ? BusinessRole.MEMBER : BusinessRole.ADMIN);
        BusinessMembership target = membershipRepository.findActive(businessId, targetUserId)
                .orElseThrow(BusinessNotFoundException::new);
        if (!leaving && actor.role() != BusinessRole.OWNER && !actor.role().outranks(target.getRole())) {
            throw new BusinessAccessDeniedException("You can only remove members ranked below you");
        }
        if (target.getRole() == BusinessRole.OWNER) {
            ensureAnotherOwner(businessId);
        }
        target.remove();
        SecurityAudit.record(Event.MEMBERSHIP_REMOVED, actorId, targetUserId,
                "business=" + businessId + (leaving ? " left" : " removed"));
    }

    /** The current OWNER hands ownership to another active member and becomes an ADMIN. */
    @Transactional
    public MemberResponse transferOwnership(UUID businessId, UUID actorId, UUID newOwnerId) {
        lockBusiness(businessId);
        access.require(businessId, actorId, BusinessRole.OWNER);
        if (actorId.equals(newOwnerId)) {
            throw new IllegalArgumentException("You already own this business");
        }
        BusinessMembership newOwner = membershipRepository.findActive(businessId, newOwnerId)
                .orElseThrow(BusinessNotFoundException::new);
        boolean newOwnerAccountActive = userRepository.findById(newOwnerId)
                .map(u -> u.getAccountStatus() == com.growthprint.backend.auth.entity.AccountStatus.ACTIVE)
                .orElse(false);
        if (!newOwnerAccountActive) {
            throw new IllegalStateException("Ownership can only be transferred to a member with an active account");
        }
        BusinessMembership current = membershipRepository.findActive(businessId, actorId)
                .orElseThrow(BusinessNotFoundException::new);
        newOwner.changeRole(BusinessRole.OWNER);
        current.changeRole(BusinessRole.ADMIN);
        SecurityAudit.record(Event.OWNERSHIP_TRANSFERRED, actorId, newOwnerId, "business=" + businessId);
        return toMember(newOwner);
    }

    // ---------------------------------------------------------------------------------------------
    // Invitations
    // ---------------------------------------------------------------------------------------------

    @Transactional
    public CreatedInvitationResponse invite(UUID businessId, UUID actorId, String email, BusinessRole role) {
        lockBusiness(businessId);
        BusinessContext actor = access.require(businessId, actorId, BusinessRole.ADMIN);
        if (role == BusinessRole.OWNER) {
            throw new IllegalArgumentException("Invite as ADMIN and transfer ownership afterwards");
        }
        if (actor.role() != BusinessRole.OWNER && !actor.role().outranks(role)) {
            throw new BusinessAccessDeniedException("You can only invite roles below your own");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        userRepository.findByNormalizedEmail(normalized)
                .flatMap(u -> membershipRepository.findActive(businessId, u.getId()))
                .ifPresent(m -> {
                    throw new IllegalStateException("This person is already a member of the business");
                });
        Instant now = clock.instant();
        invitationRepository.findPending(businessId, normalized).forEach(old -> old.revoke(now));
        invitationRepository.flush();

        String rawToken = SecureTokens.newToken();
        BusinessInvitation invitation = invitationRepository.save(new BusinessInvitation(UUID.randomUUID(), businessId,
                normalized, role, SecureTokens.sha256Hex(rawToken), now.plus(properties.getInvitationTtl()), actorId,
                now));
        SecurityAudit.record(Event.BUSINESS_INVITATION_CREATED, actorId, null,
                "business=" + businessId + " role=" + role);
        String link = properties.normalizedFrontendBaseUrl() + "/join-business#token=" + rawToken;
        return new CreatedInvitationResponse(toInvitation(invitation), rawToken, link);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listPendingInvitations(UUID businessId, UUID actorId) {
        access.require(businessId, actorId, BusinessRole.ADMIN);
        return invitationRepository.findPendingByBusiness(businessId).stream().map(this::toInvitation).toList();
    }

    @Transactional
    public void revokeInvitation(UUID businessId, UUID actorId, UUID invitationId) {
        access.require(businessId, actorId, BusinessRole.ADMIN);
        BusinessInvitation invitation = invitationRepository.findByIdAndBusinessId(invitationId, businessId)
                .orElseThrow(BusinessNotFoundException::new);
        if (BusinessInvitation.PENDING.equals(invitation.getStatus())) {
            invitation.revoke(clock.instant());
            SecurityAudit.record(Event.BUSINESS_INVITATION_REVOKED, actorId, null, "business=" + businessId);
        }
    }

    /**
     * The signed-in user joins the business. The invitation must be pending, unexpired and addressed to the
     * user's own email, so a leaked link cannot be used by a different account.
     */
    @Transactional(noRollbackFor = InvalidAccountTokenException.class)
    public BusinessResponse acceptInvitation(UUID userId, String rawToken) {
        if (!SecureTokens.looksValid(rawToken)) {
            throw new InvalidAccountTokenException();
        }
        Instant now = clock.instant();
        BusinessInvitation invitation = invitationRepository.findForUpdateByTokenHash(SecureTokens.sha256Hex(rawToken))
                .filter(i -> i.isUsable(now))
                .orElseThrow(InvalidAccountTokenException::new);
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        if (user.getRole() == UserRole.CLIENT) {
            throw new BusinessAccessDeniedException("Client portal accounts cannot join a business team");
        }
        if (!user.getNormalizedEmail().equals(invitation.getNormalizedEmail())) {
            throw new InvalidAccountTokenException();
        }
        Business business = businessRepository.findById(invitation.getBusinessId())
                .filter(Business::isActive)
                .orElseThrow(InvalidAccountTokenException::new);
        if (!inviterStillAuthorized(invitation)) {
            // The person who sent it was removed or demoted since: the invitation no longer carries authority.
            invitation.revoke(now);
            SecurityAudit.record(Event.BUSINESS_INVITATION_REVOKED, null, userId,
                    "business=" + business.getId() + " inviter-no-longer-authorized");
            // noRollbackFor on this method keeps the revocation committed while the caller gets a 400.
            throw new InvalidAccountTokenException();
        }

        BusinessMembership membership = membershipRepository.findOne(business.getId(), userId).orElse(null);
        if (membership == null) {
            membership = membershipRepository.save(new BusinessMembership(business.getId(), userId,
                    invitation.getRole(), null, now));
        } else if (!membership.isActive()) {
            membership.reactivate(invitation.getRole(), null);
        } else {
            // Already an active member: close the invitation so it cannot be reused, keep the current role
            // (an invitation never changes an existing member's role).
            invitation.accept(userId, now);
            SecurityAudit.record(Event.BUSINESS_INVITATION_ACCEPTED, userId, userId,
                    "business=" + business.getId() + " already-member role-unchanged");
            return new BusinessResponse(business.getId(), business.getName(), business.getSlug(),
                    business.getStatus(), membership.getRole().name(), business.getCreatedAt());
        }
        invitation.accept(userId, now);
        SecurityAudit.record(Event.BUSINESS_INVITATION_ACCEPTED, userId, userId,
                "business=" + business.getId() + " role=" + invitation.getRole());
        return new BusinessResponse(business.getId(), business.getName(), business.getSlug(), business.getStatus(),
                membership.getRole().name(), business.getCreatedAt());
    }

    // ---------------------------------------------------------------------------------------------

    /** The inviter must still be an active member who could send this invitation today. */
    private boolean inviterStillAuthorized(BusinessInvitation invitation) {
        if (invitation.getInvitedByUserId() == null) {
            return false;
        }
        return membershipRepository.findActive(invitation.getBusinessId(), invitation.getInvitedByUserId())
                .map(m -> m.getRole() == BusinessRole.OWNER
                        || (m.getRole().atLeast(BusinessRole.ADMIN) && m.getRole().outranks(invitation.getRole())))
                .orElse(false);
    }

    private void lockBusiness(UUID businessId) {
        businessRepository.findForUpdateById(businessId).orElseThrow(BusinessNotFoundException::new);
    }

    private void ensureAnotherOwner(UUID businessId) {
        if (membershipRepository.countActiveWithRole(businessId, BusinessRole.OWNER) <= 1) {
            throw new IllegalStateException("A business must keep at least one owner. Transfer ownership first.");
        }
    }

    private MemberResponse toMember(BusinessMembership m) {
        User u = userRepository.findById(m.getUserId()).orElse(null);
        return new MemberResponse(m.getUserId(), u == null ? null : u.getEmail(), u == null ? null : u.getFullName(),
                m.getRole().name(), m.getCreatedAt());
    }

    private InvitationResponse toInvitation(BusinessInvitation i) {
        return new InvitationResponse(i.getId(), i.getNormalizedEmail(), i.getRole().name(), i.getStatus(),
                i.getExpiresAt(), i.getCreatedAt());
    }
}
