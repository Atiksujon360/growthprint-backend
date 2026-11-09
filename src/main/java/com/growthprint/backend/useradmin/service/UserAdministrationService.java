package com.growthprint.backend.useradmin.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.ClientRole;
import com.growthprint.backend.auth.entity.TeamModule;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserInvitation;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.exception.UserNotFoundException;
import com.growthprint.backend.auth.notification.AccountEmailSender;
import com.growthprint.backend.auth.repository.UserInvitationRepository;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.auth.service.PasswordPolicy;
import com.growthprint.backend.auth.service.PasswordRecoveryService;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.config.AccountSecurityProperties;
import com.growthprint.backend.config.JwtProperties;
import com.growthprint.backend.security.SecureTokens;
import com.growthprint.backend.security.SecurityAudit;
import com.growthprint.backend.security.SecurityAudit.Event;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.InvitationResponse;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.InviteUserRequest;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.ManagedUserResponse;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.OneTimeLink;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.SecurityOverviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ADMIN-only management of users, staff (TEAM_MEMBER) and their permissions. Guard rails:
 * <ul>
 *     <li>an admin can never change their own status or role (no self-lockout, no self-elevation path);</li>
 *     <li>the last active admin can never be suspended, disabled or demoted;</li>
 *     <li>client logins stay client logins (no conversion to staff and back);</li>
 *     <li>every status/role change revokes the target's existing tokens immediately;</li>
 *     <li>accounts are never deleted here (deactivate instead) so history and audit references remain.</li>
 * </ul>
 */
@Service
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final UserInvitationRepository invitationRepository;
    private final ClientRepository clientRepository;
    private final PasswordRecoveryService passwordRecoveryService;
    private final AccountEmailSender emailSender;
    private final AccountSecurityProperties properties;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public UserAdministrationService(UserRepository userRepository, UserInvitationRepository invitationRepository,
                                     ClientRepository clientRepository,
                                     PasswordRecoveryService passwordRecoveryService,
                                     AccountEmailSender emailSender, AccountSecurityProperties properties,
                                     JwtProperties jwtProperties, Clock clock) {
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.clientRepository = clientRepository;
        this.passwordRecoveryService = passwordRecoveryService;
        this.emailSender = emailSender;
        this.properties = properties;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /** All users, optionally filtered by role and/or client. */
    @Transactional(readOnly = true)
    public List<ManagedUserResponse> listUsers(UserRole role, UUID clientId) {
        List<User> users = userRepository.findAllWithPermissions().stream()
                .filter(user -> role == null || user.getRole() == role)
                .filter(user -> clientId == null || clientId.equals(user.getClientId()))
                .toList();
        Set<UUID> clientIds = users.stream().map(User::getClientId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> clientNames = clientIds.isEmpty() ? Map.of()
                : clientRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(Client::getId, Client::getName, (a, b) -> a));
        return users.stream().map(user -> ManagedUserResponse.from(user, clientNames.get(user.getClientId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagedUserResponse getUser(UUID id) {
        return view(userRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found")));
    }

    /** Creates an INVITED account (no password) and returns its one-time setup link. */
    @Transactional
    public InvitationResponse invite(UUID adminId, InviteUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            throw new IllegalStateException("An account with this email already exists");
        }
        UUID clientId = null;
        ClientRole clientRole = null;
        if (request.role() == UserRole.CLIENT) {
            if (request.clientId() == null) {
                throw new IllegalArgumentException("Client users must belong to a client");
            }
            if (!clientRepository.existsById(request.clientId())) {
                throw new IllegalArgumentException("Client not found");
            }
            clientId = request.clientId();
            clientRole = request.clientRole() == null ? ClientRole.CLIENT_OWNER : request.clientRole();
        } else if (request.clientId() != null || request.clientRole() != null) {
            throw new IllegalArgumentException("Only client users can belong to a client");
        }
        if (request.role() != UserRole.TEAM_MEMBER && request.permissions() != null
                && !request.permissions().isEmpty()) {
            throw new IllegalArgumentException("Module permissions apply to team members only");
        }

        Instant now = clock.instant();
        User user = new User(UUID.randomUUID(), request.email().trim(), normalizedEmail, null, request.role(),
                AccountStatus.INVITED, clientId, now, now);
        user.assignInvitationDetails(request.fullName().trim(), clientRole, adminId);
        if (request.role() == UserRole.TEAM_MEMBER) {
            user.replacePermissions(request.permissions());
        }
        userRepository.save(user);

        OneTimeLink link = newInvitation(user, adminId, now);
        SecurityAudit.record(Event.INVITATION_CREATED, adminId, user.getId(), request.role().name());
        return new InvitationResponse(view(user), link);
    }

    /** Issues a fresh setup link for an account that has not accepted its invitation; older links stop working. */
    @Transactional
    public OneTimeLink reissueInvitation(UUID adminId, UUID userId) {
        User user = lock(userId);
        if (user.getAccountStatus() != AccountStatus.INVITED) {
            throw new IllegalStateException("This account has already been set up");
        }
        OneTimeLink link = newInvitation(user, adminId, clock.instant());
        SecurityAudit.record(Event.INVITATION_CREATED, adminId, userId, "reissued");
        return link;
    }

    /** One-time password reset link for an active user (for when email delivery is not configured). */
    @Transactional
    public OneTimeLink issuePasswordResetLink(UUID adminId, UUID userId) {
        User user = lock(userId);
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Password reset links can only be issued for active accounts");
        }
        Instant now = clock.instant();
        String link = passwordRecoveryService.issueResetLink(user, now);
        SecurityAudit.record(Event.PASSWORD_RESET_REQUESTED, adminId, userId, "admin-issued-link");
        return new OneTimeLink(link.substring(link.indexOf("#token=") + "#token=".length()), link,
                now.plus(properties.getPasswordResetTtl()));
    }

    @Transactional
    public ManagedUserResponse changeStatus(UUID adminId, UUID userId, AccountStatus status) {
        if (adminId.equals(userId)) {
            throw new IllegalArgumentException("You cannot change the status of your own account");
        }
        if (status == AccountStatus.INVITED) {
            throw new IllegalArgumentException("Use 'resend invitation' to invite a user again");
        }
        User user = lock(userId);
        if (status == AccountStatus.ACTIVE && user.getPasswordHash() == null) {
            throw new IllegalStateException("This user has not accepted the invitation yet");
        }
        if (status != AccountStatus.ACTIVE) {
            ensureNotLastActiveAdmin(user);
        }
        AccountStatus previous = user.getAccountStatus();
        user.changeStatus(status);
        if (status != AccountStatus.ACTIVE) {
            revokePendingInvitations(userId, clock.instant());
        }
        SecurityAudit.record(Event.ACCOUNT_STATUS_CHANGED, adminId, userId, previous + "->" + status);
        return view(user);
    }

    @Transactional
    public ManagedUserResponse changeRole(UUID adminId, UUID userId, UserRole role) {
        if (adminId.equals(userId)) {
            throw new IllegalArgumentException("You cannot change your own role");
        }
        if (role == UserRole.CLIENT) {
            throw new IllegalArgumentException("Staff accounts cannot be turned into client logins");
        }
        User user = lock(userId);
        if (user.getRole() == UserRole.ADMIN && role != UserRole.ADMIN) {
            ensureNotLastActiveAdmin(user);
        }
        UserRole previous = user.getRole();
        user.changeInternalRole(role);
        SecurityAudit.record(Event.ROLE_CHANGED, adminId, userId, previous + "->" + role);
        return view(user);
    }

    @Transactional
    public ManagedUserResponse replacePermissions(UUID adminId, UUID userId, Set<TeamModule> modules) {
        User user = lock(userId);
        user.replacePermissions(modules == null ? EnumSet.noneOf(TeamModule.class) : modules);
        SecurityAudit.record(Event.PERMISSIONS_CHANGED, adminId, userId,
                String.join(",", user.getPermissions().stream().map(Enum::name).sorted().toList()));
        return view(user);
    }

    @Transactional(readOnly = true)
    public SecurityOverviewResponse securityOverview() {
        return new SecurityOverviewResponse(
                emailSender.isConfigured(),
                jwtProperties.getExpiration().toMinutes(),
                properties.getPasswordResetTtl().toMinutes(),
                properties.getInvitationTtl().toHours(),
                PasswordPolicy.MIN_LENGTH,
                PasswordPolicy.DESCRIPTION,
                Arrays.stream(TeamModule.values()).map(Enum::name).toList(),
                userRepository.countByRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE));
    }

    private ManagedUserResponse view(User user) {
        String clientName = user.getClientId() == null ? null
                : clientRepository.findById(user.getClientId()).map(Client::getName).orElse(null);
        return ManagedUserResponse.from(user, clientName);
    }

    private OneTimeLink newInvitation(User user, UUID adminId, Instant now) {
        revokePendingInvitations(user.getId(), now);
        // Flush the revocations first: the partial unique index allows only one PENDING invitation per user.
        invitationRepository.flush();
        String rawToken = SecureTokens.newToken();
        Instant expiresAt = now.plus(properties.getInvitationTtl());
        invitationRepository.save(new UserInvitation(UUID.randomUUID(), user.getId(),
                SecureTokens.sha256Hex(rawToken), expiresAt, adminId, now));
        String link = properties.normalizedFrontendBaseUrl() + "/accept-invite#token=" + rawToken;
        return new OneTimeLink(rawToken, link, expiresAt);
    }

    private void revokePendingInvitations(UUID userId, Instant now) {
        invitationRepository.findByUserIdAndStatus(userId, UserInvitation.PENDING)
                .forEach(invitation -> invitation.revoke(now));
    }

    private void ensureNotLastActiveAdmin(User user) {
        if (user.getRole() == UserRole.ADMIN && user.getAccountStatus() == AccountStatus.ACTIVE
                && userRepository.countByRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE) <= 1) {
            throw new IllegalStateException("The last active administrator cannot be deactivated or demoted");
        }
    }

    private User lock(UUID userId) {
        return userRepository.findForUpdateById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
