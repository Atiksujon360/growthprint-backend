package com.growthprint.backend.auth.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 254, unique = true)
    private String normalizedEmail;

    /** BCrypt hash; NULL only while the account is INVITED (V17 chk_users_password_set). */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(name = "client_id")
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_role", length = 40)
    private ClientRole clientRole;

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(length = 40)
    private String phone;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(length = 64)
    private String timezone;

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    /** Embedded in every JWT ("ver" claim); incrementing it revokes all previously issued tokens (V20). */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "team_member_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module", length = 30, nullable = false)
    private Set<TeamModule> permissions = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(UUID id, String email, String normalizedEmail, String passwordHash,
                UserRole role, AccountStatus accountStatus, UUID clientId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
        this.role = role;
        this.accountStatus = accountStatus;
        this.clientId = clientId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        // V17 chk_users_client_membership: client logins need a client role. Owner keeps the pre-V17 behaviour.
        if (role == UserRole.CLIENT && clientId != null) {
            this.clientRole = ClientRole.CLIENT_OWNER;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ---------------------------------------------------------------------------------------------
    // Behaviour: state changes go through these methods so the invariants live in one place.
    // ---------------------------------------------------------------------------------------------

    /** Sets a new password hash and revokes every token issued before. */
    public void changePasswordHash(String newPasswordHash, Instant now) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = now;
        revokeAllTokens();
    }

    /** Invalidates every JWT issued for this user so far ("sign out everywhere"). */
    public void revokeAllTokens() {
        this.tokenVersion++;
    }

    /** Changes the account status; any change revokes existing tokens. */
    public void changeStatus(AccountStatus newStatus) {
        if (this.accountStatus != newStatus) {
            this.accountStatus = newStatus;
            revokeAllTokens();
        }
    }

    /** Switches between the internal roles (ADMIN / TEAM_MEMBER). Client logins never change role. */
    public void changeInternalRole(UserRole newRole) {
        if (newRole == UserRole.CLIENT || this.role == UserRole.CLIENT) {
            throw new IllegalArgumentException("Client logins cannot be converted to or from staff roles");
        }
        if (this.role != newRole) {
            this.role = newRole;
            if (newRole == UserRole.ADMIN) {
                this.permissions.clear();
            }
            revokeAllTokens();
        }
    }

    /** Replaces the module permissions of a team member. */
    public void replacePermissions(Set<TeamModule> modules) {
        if (role != UserRole.TEAM_MEMBER) {
            throw new IllegalArgumentException("Module permissions apply to team members only");
        }
        this.permissions.clear();
        if (modules != null) {
            this.permissions.addAll(modules);
        }
    }

    public void updateProfile(String fullName, String phone, String jobTitle, String timezone,
                              boolean emailNotifications) {
        this.fullName = fullName;
        this.phone = phone;
        this.jobTitle = jobTitle;
        this.timezone = timezone;
        this.emailNotifications = emailNotifications;
    }

    public void assignInvitationDetails(String fullName, ClientRole clientRole, UUID createdByUserId) {
        this.fullName = fullName;
        this.clientRole = role == UserRole.CLIENT ? clientRole : null;
        this.createdByUserId = createdByUserId;
    }

    /** Sets the first password of an invited account and activates it. */
    public void activateWithPassword(String passwordHash, Instant now) {
        if (accountStatus != AccountStatus.INVITED) {
            throw new IllegalStateException("Account is not awaiting activation");
        }
        this.accountStatus = AccountStatus.ACTIVE;
        changePasswordHash(passwordHash, now);
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
    }

    // ---------------------------------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public UUID getClientId() {
        return clientId;
    }

    public ClientRole getClientRole() {
        return clientRole;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isEmailNotifications() {
        return emailNotifications;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    /** Copy of the granted modules (always empty for anyone but TEAM_MEMBER). */
    public Set<TeamModule> getPermissions() {
        if (role != UserRole.TEAM_MEMBER || permissions.isEmpty()) {
            return EnumSet.noneOf(TeamModule.class);
        }
        return EnumSet.copyOf(permissions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
