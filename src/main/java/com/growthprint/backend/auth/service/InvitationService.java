package com.growthprint.backend.auth.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserInvitation;
import com.growthprint.backend.auth.exception.InvalidAccountTokenException;
import com.growthprint.backend.auth.exception.WeakPasswordException;
import com.growthprint.backend.auth.repository.UserInvitationRepository;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.client.entity.Client;
import com.growthprint.backend.client.repository.ClientRepository;
import com.growthprint.backend.security.SecureTokens;
import com.growthprint.backend.security.SecurityAudit;
import com.growthprint.backend.security.SecurityAudit.Event;
import com.growthprint.backend.useradmin.dto.UserAdminDtos.InvitationPreviewResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Accepting an admin-issued invitation: the invitee sets their first password and the account becomes ACTIVE. */
@Service
public class InvitationService {

    private final UserInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public InvitationService(UserInvitationRepository invitationRepository, UserRepository userRepository,
                             ClientRepository clientRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** What the invitee may see before choosing a password. Invalid/expired/used links all give the same 400. */
    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String rawToken) {
        UserInvitation invitation = lookup(rawToken, false);
        if (!invitation.isUsable(clock.instant())) {
            throw new InvalidAccountTokenException();
        }
        User user = userRepository.findById(invitation.getUserId())
                .filter(u -> u.getAccountStatus() == AccountStatus.INVITED)
                .orElseThrow(InvalidAccountTokenException::new);
        String clientName = user.getClientId() == null ? null
                : clientRepository.findById(user.getClientId()).map(Client::getName).orElse(null);
        return new InvitationPreviewResponse(user.getEmail(), user.getFullName(), user.getRole().name(), clientName);
    }

    /** Sets the first password and activates the account. Single use. @return the activated user's id */
    @Transactional
    public UUID accept(String rawToken, String password) {
        Instant now = clock.instant();
        UserInvitation invitation = lookup(rawToken, true);
        if (!invitation.isUsable(now)) {
            SecurityAudit.record(Event.INVITATION_REJECTED, null, invitation.getUserId(), invitation.getStatus());
            throw new InvalidAccountTokenException();
        }
        User user = userRepository.findForUpdateById(invitation.getUserId())
                .filter(u -> u.getAccountStatus() == AccountStatus.INVITED)
                .orElseThrow(InvalidAccountTokenException::new);
        List<String> problems = PasswordPolicy.violations(password, user.getEmail());
        if (!problems.isEmpty()) {
            throw new WeakPasswordException("password", problems);
        }
        invitation.accept(now);
        user.activateWithPassword(passwordEncoder.encode(password), now);
        SecurityAudit.record(Event.INVITATION_ACCEPTED, user.getId(), user.getId(), user.getRole().name());
        return user.getId();
    }

    private UserInvitation lookup(String rawToken, boolean forUpdate) {
        if (!SecureTokens.looksValid(rawToken)) {
            throw new InvalidAccountTokenException();
        }
        String hash = SecureTokens.sha256Hex(rawToken);
        return (forUpdate ? invitationRepository.findForUpdateByTokenHash(hash)
                : invitationRepository.findByTokenHash(hash))
                .orElseThrow(InvalidAccountTokenException::new);
    }
}
