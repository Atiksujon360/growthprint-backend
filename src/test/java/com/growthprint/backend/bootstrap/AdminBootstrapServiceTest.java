package com.growthprint.backend.bootstrap;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.bootstrap.AdminBootstrapService.Outcome;
import com.growthprint.backend.business.entity.Business;
import com.growthprint.backend.business.entity.BusinessMembership;
import com.growthprint.backend.business.entity.BusinessRole;
import com.growthprint.backend.business.repository.BusinessMembershipRepository;
import com.growthprint.backend.business.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminBootstrapServiceTest {

    private static final String EMAIL = "owner@studio.test";
    private static final String GOOD_PASSWORD = "Vermilion-Harbour-4821";

    private UserRepository users;
    private BusinessRepository businesses;
    private BusinessMembershipRepository memberships;
    private PasswordEncoder encoder;
    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        businesses = mock(BusinessRepository.class);
        memberships = mock(BusinessMembershipRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("{hash}");
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findByNormalizedEmail(any())).thenReturn(Optional.empty());
        when(businesses.existsById(Business.DEFAULT_BUSINESS_ID)).thenReturn(true);
        service = new AdminBootstrapService(users, businesses, memberships, encoder);
    }

    private void noActiveAdmin() {
        when(users.countByRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE)).thenReturn(0L);
    }

    @Test
    void doesNothingWhenNotConfigured() {
        assertEquals(Outcome.NOT_CONFIGURED, service.bootstrap("", ""));
        assertEquals(Outcome.NOT_CONFIGURED, service.bootstrap(null, null));
        verifyNoInteractions(users, memberships);
    }

    @Test
    void rejectsHalfConfiguredSettings() {
        assertThrows(IllegalStateException.class, () -> service.bootstrap(EMAIL, ""));
        assertThrows(IllegalStateException.class, () -> service.bootstrap("", GOOD_PASSWORD));
    }

    @Test
    void rejectsWeakPasswordsWithoutEchoingThem() {
        noActiveAdmin();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.bootstrap(EMAIL, "admin123"));
        assertFalse(e.getMessage().contains("admin123"));
        verify(users, never()).save(any());
    }

    @Test
    void rejectsAnInvalidEmail() {
        assertThrows(IllegalStateException.class, () -> service.bootstrap("not-an-email", GOOD_PASSWORD));
    }

    @Test
    void createsAnActiveAdminWithAHashedPasswordAndOwnsTheDefaultBusiness() {
        noActiveAdmin();
        when(memberships.countActiveWithRole(Business.DEFAULT_BUSINESS_ID, BusinessRole.OWNER)).thenReturn(0L);
        when(memberships.findOne(any(), any())).thenReturn(Optional.empty());

        assertEquals(Outcome.CREATED, service.bootstrap("  Owner@Studio.test ", GOOD_PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals(UserRole.ADMIN, saved.getValue().getRole());
        assertEquals(AccountStatus.ACTIVE, saved.getValue().getAccountStatus());
        assertEquals(EMAIL, saved.getValue().getNormalizedEmail());
        assertEquals("{hash}", saved.getValue().getPasswordHash());
        verify(memberships).save(any(BusinessMembership.class));
    }

    @Test
    void doesNotCreateAnotherAdminWhenOneIsActive() {
        when(users.countByRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE)).thenReturn(1L);
        when(memberships.countActiveWithRole(Business.DEFAULT_BUSINESS_ID, BusinessRole.OWNER)).thenReturn(1L);

        assertEquals(Outcome.ALREADY_EXISTS, service.bootstrap(EMAIL, GOOD_PASSWORD));
        verify(users, never()).save(any());
        verify(memberships, never()).save(any());
    }

    @Test
    void neverChangesAnExistingAccountsPassword() {
        noActiveAdmin();
        User existing = new User(UUID.randomUUID(), EMAIL, EMAIL, "{old}", UserRole.ADMIN, AccountStatus.SUSPENDED,
                null, Instant.now(), Instant.now());
        when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(existing));

        assertEquals(Outcome.ALREADY_EXISTS, service.bootstrap(EMAIL, GOOD_PASSWORD));
        assertEquals("{old}", existing.getPasswordHash());
        verify(users, never()).save(any());
    }

    @Test
    void restoresTheOwnerRoleOfAnExistingActiveAdminWhenTheDefaultBusinessHasNoOwner() {
        when(users.countByRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE)).thenReturn(1L);
        User admin = new User(UUID.randomUUID(), EMAIL, EMAIL, "{old}", UserRole.ADMIN, AccountStatus.ACTIVE,
                null, Instant.now(), Instant.now());
        when(users.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(admin));
        when(memberships.countActiveWithRole(Business.DEFAULT_BUSINESS_ID, BusinessRole.OWNER)).thenReturn(0L);
        when(memberships.findOne(any(), any())).thenReturn(Optional.empty());

        assertEquals(Outcome.OWNER_RESTORED, service.bootstrap(EMAIL, GOOD_PASSWORD));
        verify(memberships).save(any(BusinessMembership.class));
        verify(users, never()).save(any());
    }
}
