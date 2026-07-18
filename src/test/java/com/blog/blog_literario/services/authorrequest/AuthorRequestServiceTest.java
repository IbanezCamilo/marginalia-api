package com.blog.blog_literario.services.authorrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.blog.blog_literario.config.properties.AuthorRequestProperties;
import com.blog.blog_literario.dto.authorrequest.AuthorRequestResponse;
import com.blog.blog_literario.events.AuthorRequestSubmitted;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.AuthorRequest;
import com.blog.blog_literario.model.AuthorRequestStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.AuthorRequestRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.users.UserUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AuthorRequestServiceTest {

    @Mock AuthorRequestRepository authorRequestRepository;
    @Mock UserRepository userRepository;
    @Mock UserUpdateService userUpdateService;
    @Mock AuthorRequestProperties authorRequestProperties;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks AuthorRequestService service;

    private User reader;
    private User adminUser;
    private User otherAdmin;

    @BeforeEach
    void setUp() {
        reader = new User(1, "Reader", "reader@test.com", new Role("READER"));
        adminUser = new User(2, "Admin", "admin@test.com", new Role("ADMIN"));
        otherAdmin = new User(3, "Otra Admin", "otra@test.com", new Role("ADMIN"));
    }

    @Test
    void createRequest_readerNoPendingRequest_succeeds() {
        given(userRepository.findById(1)).willReturn(Optional.of(reader));
        given(authorRequestRepository.existsByRequesterIdAndStatus(1, AuthorRequestStatus.PENDING)).willReturn(false);
        given(authorRequestRepository.save(any())).willAnswer(inv -> {
            AuthorRequest r = inv.getArgument(0);
            r.setId(1);
            return r;
        });

        AuthorRequestResponse result = service.createRequest(1, "I want to write");

        assertThat(result.requesterId()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    void createRequest_nonReaderRole_throwsIllegalState() {
        User author = new User(3, "Author", "author@test.com", new Role("AUTHOR"));
        given(userRepository.findById(3)).willReturn(Optional.of(author));

        assertThatThrownBy(() -> service.createRequest(3, "motivation"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READER role");
    }

    @Test
    void createRequest_pendingAlreadyExists_throwsIllegalState() {
        given(userRepository.findById(1)).willReturn(Optional.of(reader));
        given(authorRequestRepository.existsByRequesterIdAndStatus(1, AuthorRequestStatus.PENDING)).willReturn(true);

        assertThatThrownBy(() -> service.createRequest(1, "motivation"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending request");
    }

    @Test
    void createRequest_publishesEventWithAdminEmailsAndPerRequestKey() {
        given(userRepository.findById(1)).willReturn(Optional.of(reader));
        given(authorRequestRepository.existsByRequesterIdAndStatus(1, AuthorRequestStatus.PENDING)).willReturn(false);
        given(authorRequestRepository.save(any())).willAnswer(inv -> {
            AuthorRequest r = inv.getArgument(0);
            r.setId(7);
            return r;
        });
        given(userRepository.findEmailsByRoleNames(List.of(Role.ADMIN, Role.OWNER)))
                .willReturn(List.of("admin@test.com", "owner@test.com"));

        service.createRequest(1, "I want to write");

        ArgumentCaptor<AuthorRequestSubmitted> eventCaptor = ArgumentCaptor.forClass(AuthorRequestSubmitted.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        AuthorRequestSubmitted event = eventCaptor.getValue();
        assertThat(event.requestId()).isEqualTo(7);
        assertThat(event.requesterName()).isEqualTo("Reader");
        assertThat(event.requesterEmail()).isEqualTo("reader@test.com");
        assertThat(event.motivation()).isEqualTo("I want to write");
        assertThat(event.adminEmails()).containsExactly("admin@test.com", "owner@test.com");
        assertThat(event.idempotencyKey()).isEqualTo("author-request/7");
    }

    @Test
    void createRequest_validationFails_publishesNoEvent() {
        given(userRepository.findById(1)).willReturn(Optional.of(reader));
        given(authorRequestRepository.existsByRequesterIdAndStatus(1, AuthorRequestStatus.PENDING)).willReturn(true);

        assertThatThrownBy(() -> service.createRequest(1, "motivation"))
                .isInstanceOf(IllegalStateException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void getMyActiveRequest_noneExists_throwsResourceNotFound() {
        given(authorRequestRepository.findByRequesterIdAndStatus(1, AuthorRequestStatus.PENDING))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyActiveRequest(1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approve_pendingRequest_promotesRoleAndSetsApproved() {
        // Role promotion (guard + mutation + audit log) is delegated to
        // UserUpdateService.updateRole() — see UserUpdateServiceTest for that behavior.
        AuthorRequest request = buildPendingRequest();
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));
        given(userRepository.findById(2)).willReturn(Optional.of(adminUser));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(authorRequestRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AuthorRequestResponse result = service.approve(1, 2, "Welcome!");

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(userUpdateService).updateRole(reader, Role.AUTHOR, 2);
        verify(userRepository).save(reader);
    }

    @Test
    void approve_roleUpdateGuardThrows_propagatesException() {
        // Defense-in-depth: approve() always promotes the requester to AUTHOR, and only
        // READERs can submit requests, so this path never realistically reaches an OWNER
        // requester — but UserUpdateService.updateRole()'s target-is-OWNER guard (see
        // UserUpdateServiceTest) is shared infrastructure, so this just confirms approve()
        // propagates the exception rather than swallowing it.
        AuthorRequest request = buildPendingRequest();
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));
        given(userRepository.findById(2)).willReturn(Optional.of(adminUser));
        doThrow(new IllegalStateException("El rol OWNER no puede ser modificado"))
                .when(userUpdateService).updateRole(reader, Role.AUTHOR, 2);

        assertThatThrownBy(() -> service.approve(1, 2, "note"))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void approve_alreadyApproved_throwsIllegalState() {
        AuthorRequest request = buildPendingRequest();
        request.setStatus(AuthorRequestStatus.APPROVED);
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(1, 2, "note"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been resolved");
    }

    @Test
    void reject_pendingRequest_keepsReaderRoleAndDoesNotSaveUser() {
        AuthorRequest request = buildPendingRequest();
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));
        given(userRepository.findById(2)).willReturn(Optional.of(adminUser));
        given(authorRequestRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AuthorRequestResponse result = service.reject(1, 2, "Not enough content");

        assertThat(result.status()).isEqualTo("REJECTED");
        verify(userRepository, never()).save(reader);
    }

    @Test
    void countPending_delegatesToRepository() {
        given(authorRequestRepository.countByStatus(AuthorRequestStatus.PENDING)).willReturn(5L);

        assertThat(service.countPending()).isEqualTo(5L);
    }

    // ─── Review claims ─────────────────────────────────────────────────────────

    @Test
    void claim_unclaimedRequest_assignsClaimToAdmin() {
        AuthorRequest request = buildPendingRequest();
        given(authorRequestProperties.claimTtlMinutes()).willReturn(10L);
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));
        given(userRepository.findById(2)).willReturn(Optional.of(adminUser));
        given(authorRequestRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AuthorRequestResponse result = service.claim(1, 2);

        assertThat(result.claimedById()).isEqualTo(2);
        assertThat(result.claimedByName()).isEqualTo("Admin");
        assertThat(result.claimedAt()).isNotNull();
    }

    @Test
    void claim_ownExistingClaim_refreshesTimestamp() {
        AuthorRequest request = buildClaimedRequest(adminUser, LocalDateTime.now().minusMinutes(5));
        LocalDateTime previousClaimedAt = request.getClaimedAt();
        given(authorRequestProperties.claimTtlMinutes()).willReturn(10L);
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));
        given(userRepository.findById(2)).willReturn(Optional.of(adminUser));
        given(authorRequestRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AuthorRequestResponse result = service.claim(1, 2);

        assertThat(result.claimedById()).isEqualTo(2);
        assertThat(result.claimedAt()).isAfter(previousClaimedAt);
    }

    @Test
    void claim_activeClaimByOtherAdmin_throwsIllegalStateWithHolderName() {
        AuthorRequest request = buildClaimedRequest(otherAdmin, LocalDateTime.now().minusMinutes(1));
        given(authorRequestProperties.claimTtlMinutes()).willReturn(10L);
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.claim(1, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Otra Admin");

        verify(authorRequestRepository, never()).save(any());
    }

    @Test
    void claim_expiredClaimByOtherAdmin_reassignsToNewAdmin() {
        AuthorRequest request = buildClaimedRequest(otherAdmin, LocalDateTime.now().minusMinutes(11));
        given(authorRequestProperties.claimTtlMinutes()).willReturn(10L);
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));
        given(userRepository.findById(2)).willReturn(Optional.of(adminUser));
        given(authorRequestRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AuthorRequestResponse result = service.claim(1, 2);

        assertThat(result.claimedById()).isEqualTo(2);
    }

    @Test
    void claim_resolvedRequest_throwsIllegalState() {
        AuthorRequest request = buildPendingRequest();
        request.setStatus(AuthorRequestStatus.APPROVED);
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.claim(1, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been resolved");
    }

    @Test
    void release_ownClaim_clearsClaim() {
        AuthorRequest request = buildClaimedRequest(adminUser, LocalDateTime.now().minusMinutes(1));
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));

        service.release(1, 2);

        assertThat(request.getClaimedBy()).isNull();
        assertThat(request.getClaimedAt()).isNull();
        verify(authorRequestRepository).save(request);
    }

    @Test
    void release_claimHeldByOtherAdmin_isNoOp() {
        AuthorRequest request = buildClaimedRequest(otherAdmin, LocalDateTime.now().minusMinutes(1));
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));

        service.release(1, 2);

        assertThat(request.getClaimedBy()).isEqualTo(otherAdmin);
        verify(authorRequestRepository, never()).save(any());
    }

    @Test
    void release_unclaimedRequest_isNoOp() {
        AuthorRequest request = buildPendingRequest();
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));

        service.release(1, 2);

        verify(authorRequestRepository, never()).save(any());
    }

    @Test
    void approve_activeClaimByOtherAdmin_throwsBeforeRoleUpdate() {
        AuthorRequest request = buildClaimedRequest(otherAdmin, LocalDateTime.now().minusMinutes(1));
        given(authorRequestProperties.claimTtlMinutes()).willReturn(10L);
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(1, 2, "note"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Otra Admin");

        verify(userUpdateService, never()).updateRole(any(), any(), any());
        verify(authorRequestRepository, never()).save(any());
    }

    @Test
    void approve_ownClaim_succeedsAndClearsClaim() {
        AuthorRequest request = buildClaimedRequest(adminUser, LocalDateTime.now().minusMinutes(1));
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));
        given(userRepository.findById(2)).willReturn(Optional.of(adminUser));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(authorRequestRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AuthorRequestResponse result = service.approve(1, 2, "Welcome!");

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.claimedById()).isNull();
        assertThat(result.claimedAt()).isNull();
    }

    @Test
    void reject_expiredClaimByOtherAdmin_succeeds() {
        AuthorRequest request = buildClaimedRequest(otherAdmin, LocalDateTime.now().minusMinutes(11));
        given(authorRequestProperties.claimTtlMinutes()).willReturn(10L);
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));
        given(userRepository.findById(2)).willReturn(Optional.of(adminUser));
        given(authorRequestRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AuthorRequestResponse result = service.reject(1, 2, "note");

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.claimedById()).isNull();
    }

    @Test
    void getMyActiveRequest_activeClaim_exposesClaimFields() {
        AuthorRequest request = buildClaimedRequest(otherAdmin, LocalDateTime.now().minusMinutes(1));
        given(authorRequestProperties.claimTtlMinutes()).willReturn(10L);
        given(authorRequestRepository.findByRequesterIdAndStatus(1, AuthorRequestStatus.PENDING))
                .willReturn(Optional.of(request));

        AuthorRequestResponse result = service.getMyActiveRequest(1);

        assertThat(result.claimedById()).isEqualTo(3);
        assertThat(result.claimedByName()).isEqualTo("Otra Admin");
        assertThat(result.claimedAt()).isNotNull();
    }

    @Test
    void getMyActiveRequest_expiredClaim_exposesNoClaimFields() {
        AuthorRequest request = buildClaimedRequest(otherAdmin, LocalDateTime.now().minusMinutes(11));
        given(authorRequestProperties.claimTtlMinutes()).willReturn(10L);
        given(authorRequestRepository.findByRequesterIdAndStatus(1, AuthorRequestStatus.PENDING))
                .willReturn(Optional.of(request));

        AuthorRequestResponse result = service.getMyActiveRequest(1);

        assertThat(result.claimedById()).isNull();
        assertThat(result.claimedByName()).isNull();
        assertThat(result.claimedAt()).isNull();
    }

    private AuthorRequest buildPendingRequest() {
        AuthorRequest req = new AuthorRequest();
        req.setId(1);
        req.setRequester(reader);
        req.setMotivation("I want to write");
        req.setStatus(AuthorRequestStatus.PENDING);
        return req;
    }

    private AuthorRequest buildClaimedRequest(User claimant, LocalDateTime claimedAt) {
        AuthorRequest req = buildPendingRequest();
        req.setClaimedBy(claimant);
        req.setClaimedAt(claimedAt);
        return req;
    }
}
