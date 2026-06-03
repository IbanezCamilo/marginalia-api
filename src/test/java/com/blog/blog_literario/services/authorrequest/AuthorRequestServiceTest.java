package com.blog.blog_literario.services.authorrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import com.blog.blog_literario.dto.authorrequest.AuthorRequestResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.AuthorRequest;
import com.blog.blog_literario.model.AuthorRequestStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.AuthorRequestRepository;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorRequestServiceTest {

    @Mock AuthorRequestRepository authorRequestRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;

    @InjectMocks AuthorRequestService service;

    private User reader;
    private User adminUser;
    private Role authorRole;

    @BeforeEach
    void setUp() {
        reader = new User(1, "Reader", "reader@test.com", new Role("READER"));
        adminUser = new User(2, "Admin", "admin@test.com", new Role("ADMIN"));
        authorRole = new Role("AUTHOR");
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
    void getMyActiveRequest_noneExists_throwsResourceNotFound() {
        given(authorRequestRepository.findByRequesterIdAndStatus(1, AuthorRequestStatus.PENDING))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyActiveRequest(1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approve_pendingRequest_promotesRoleAndSetsApproved() {
        AuthorRequest request = buildPendingRequest();
        given(authorRequestRepository.findById(1)).willReturn(Optional.of(request));
        given(userRepository.findById(2)).willReturn(Optional.of(adminUser));
        given(roleRepository.findByName(Role.AUTHOR)).willReturn(Optional.of(authorRole));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(authorRequestRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AuthorRequestResponse result = service.approve(1, 2, "Welcome!");

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(userRepository).save(any(User.class));
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

    private AuthorRequest buildPendingRequest() {
        AuthorRequest req = new AuthorRequest();
        req.setId(1);
        req.setRequester(reader);
        req.setMotivation("I want to write");
        req.setStatus(AuthorRequestStatus.PENDING);
        return req;
    }
}
