package com.nitramite.porssiohjain.services;

import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.FeatureRequestEntity;
import com.nitramite.porssiohjain.entity.repository.AccountRepository;
import com.nitramite.porssiohjain.entity.repository.FeatureRequestRepository;
import com.nitramite.porssiohjain.services.models.FeatureRequestInput;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureRequestServiceTest {
    private final FeatureRequestRepository repository = mock(FeatureRequestRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private ValidatorFactory factory;
    private FeatureRequestService service;

    @BeforeEach
    void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new FeatureRequestService(repository, accounts, factory.getValidator());
        when(accounts.findById(7L)).thenReturn(Optional.of(AccountEntity.builder().id(7L).email("owner@example.com").build()));
    }

    @AfterEach
    void close() { factory.close(); }

    @Test
    void savesAccountOwnershipAndDefaultsToAccountContactWithoutChangingAccount() {
        service.submit(7L, new FeatureRequestInput("  A busy household ", " New automation\n", null));
        var captor = org.mockito.ArgumentCaptor.forClass(FeatureRequestEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(7L, captor.getValue().getAccountId());
        assertEquals("A busy household", captor.getValue().getUseCase());
        assertEquals("New automation", captor.getValue().getRequestedChanges());
        assertEquals("owner@example.com", captor.getValue().getContactEmail());
        verify(accounts, never()).save(any());
    }

    @Test
    void optionalEmailCanBeClearedOrReplaced() {
        service.submit(7L, new FeatureRequestInput("Use", "Changes", "  "));
        service.submit(7L, new FeatureRequestInput("Use", "Changes", " other@example.com "));
        var captor = org.mockito.ArgumentCaptor.forClass(FeatureRequestEntity.class);
        verify(repository, times(2)).save(captor.capture());
        assertNull(captor.getAllValues().get(0).getContactEmail());
        assertEquals("other@example.com", captor.getAllValues().get(1).getContactEmail());
    }

    @Test
    void accountWithoutEmailCanSubmit() {
        when(accounts.findById(7L)).thenReturn(Optional.of(AccountEntity.builder().id(7L).build()));
        service.submit(7L, new FeatureRequestInput("Use", "Changes", null));
        verify(repository).save(argThat(row -> row.getContactEmail() == null));
    }

    @Test
    void rejectsMissingAnswersOversizedTextAndInvalidEmailWithoutSaving() {
        for (var input : java.util.List.of(
                new FeatureRequestInput("   ", "Changes", ""),
                new FeatureRequestInput("Use", null, ""),
                new FeatureRequestInput("x".repeat(5001), "Changes", ""),
                new FeatureRequestInput("Use", "x".repeat(5001), ""),
                new FeatureRequestInput("Use", "Changes", "invalid"),
                new FeatureRequestInput("Use", "Changes", "x".repeat(255) + "@example.com"))) {
            assertThrows(IllegalArgumentException.class, () -> service.submit(7L, input));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsTextLengthBoundary() {
        service.submit(7L, new FeatureRequestInput("x".repeat(5000), "y".repeat(5000), ""));
        verify(repository).save(any());
    }

    @Test
    void listingRequiresCurrentAdminPermissionAndUsesNewestFirstPagination() {
        assertThrows(IllegalArgumentException.class, () -> service.listForAdmin(7L, 0));
        verifyNoInteractions(repository);
        when(accounts.findById(7L)).thenReturn(Optional.of(AccountEntity.builder().id(7L).admin(true).build()));
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        service.listForAdmin(7L, 2);
        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(30, captor.getValue().getPageSize());
        assertTrue(captor.getValue().getSort().getOrderFor("createdAt").isDescending());
        assertTrue(captor.getValue().getSort().getOrderFor("id").isDescending());
    }

    @Test
    void missingAccountCannotSubmitOrList() {
        assertThrows(IllegalArgumentException.class, () -> service.submit(null, new FeatureRequestInput("Use", "Changes", "")));
        assertThrows(IllegalArgumentException.class, () -> service.listForAdmin(99L, 0));
        verifyNoInteractions(repository);
    }

    @Test
    void adminCanDeleteAnotherAccountsSubmission() {
        when(accounts.findById(7L)).thenReturn(Optional.of(AccountEntity.builder().id(7L).admin(true).build()));
        var request = FeatureRequestEntity.builder().id(42L).accountId(9L).build();
        when(repository.findById(42L)).thenReturn(Optional.of(request));

        service.deleteForAdmin(7L, 42L);

        verify(repository).delete(request);
    }

    @Test
    void deletionRequiresAuthenticatedCurrentNonDemoAdmin() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteForAdmin(null, 42L));
        assertThrows(IllegalArgumentException.class, () -> service.deleteForAdmin(99L, 42L));
        assertThrows(IllegalArgumentException.class, () -> service.deleteForAdmin(7L, 42L));
        when(accounts.findById(7L)).thenReturn(Optional.of(AccountEntity.builder().id(7L).admin(true).demo(true).build()));
        assertThrows(IllegalArgumentException.class, () -> service.deleteForAdmin(7L, 42L));
        verifyNoInteractions(repository);
    }

    @Test
    void deletionRejectsMissingSubmissionWithoutDeleting() {
        when(accounts.findById(7L)).thenReturn(Optional.of(AccountEntity.builder().id(7L).admin(true).build()));
        assertThrows(IllegalArgumentException.class, () -> service.deleteForAdmin(7L, null));
        assertThrows(IllegalArgumentException.class, () -> service.deleteForAdmin(7L, 42L));
        verify(repository, never()).delete(any());
    }
}
