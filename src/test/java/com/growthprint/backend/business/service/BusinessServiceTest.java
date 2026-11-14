package com.growthprint.backend.business.service;

import com.growthprint.backend.auth.entity.AccountStatus;
import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.auth.entity.UserRole;
import com.growthprint.backend.auth.repository.UserRepository;
import com.growthprint.backend.business.dto.BusinessDtos.CreateBusinessRequest;
import com.growthprint.backend.business.exception.BusinessExceptions.BusinessAccessDeniedException;
import com.growthprint.backend.business.repository.BusinessMembershipRepository;
import com.growthprint.backend.business.repository.BusinessRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Business creation is gated while MVP1 data is unscoped (M1.1). */
class BusinessServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private final BusinessRepository businesses = mock(BusinessRepository.class);
    private final BusinessMembershipRepository memberships = mock(BusinessMembershipRepository.class);
    private final UserRepository users = mock(UserRepository.class);

    private BusinessService service(boolean selfService) {
        when(businesses.save(any())).thenAnswer(i -> i.getArgument(0));
        return new BusinessService(businesses, memberships, users, new BusinessAccessService(businesses, memberships),
                Clock.fixed(NOW, ZoneOffset.UTC), selfService);
    }

    private UUID userWith(UserRole role) {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(new User(id, "u@example.test", "u@example.test", "h", role,
                AccountStatus.ACTIVE, role == UserRole.CLIENT ? UUID.randomUUID() : null, NOW, NOW)));
        return id;
    }

    @Test
    void byDefaultOnlyGlobalAdminsCanCreateBusinesses() {
        BusinessService service = service(false);
        UUID member = userWith(UserRole.TEAM_MEMBER);
        assertThatThrownBy(() -> service.create(member, new CreateBusinessRequest("Acme", null)))
                .isInstanceOf(BusinessAccessDeniedException.class);
        verify(businesses, never()).save(any());

        UUID admin = userWith(UserRole.ADMIN);
        assertThat(service.create(admin, new CreateBusinessRequest("Acme Cleaning", null)).myRole()).isEqualTo("OWNER");
    }

    @Test
    void clientPortalAccountsCanNeverCreateBusinesses() {
        BusinessService service = service(true);
        UUID client = userWith(UserRole.CLIENT);
        assertThatThrownBy(() -> service.create(client, new CreateBusinessRequest("Acme", null)))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void selfServiceFlagOpensCreationToStaffOnly() {
        BusinessService service = service(true);
        UUID member = userWith(UserRole.TEAM_MEMBER);
        assertThat(service.create(member, new CreateBusinessRequest("Globex", null)).slug()).isEqualTo("globex");
    }

    @Test
    void slugsAreDerivedSafely() {
        assertThat(BusinessService.slugify("Café Déjà Vu & Co.")).isEqualTo("cafe-deja-vu-co");
        assertThat(BusinessService.slugify("!!")).isEqualTo("business");
    }
}
