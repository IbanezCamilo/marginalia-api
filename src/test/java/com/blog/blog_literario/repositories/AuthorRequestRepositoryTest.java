package com.blog.blog_literario.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import com.blog.blog_literario.model.AuthorRequest;
import com.blog.blog_literario.model.AuthorRequestStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;

@DataJpaTest
@ActiveProfiles("test")
class AuthorRequestRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired AuthorRequestRepository authorRequestRepository;
    @Autowired RoleRepository roleRepository;

    private User requester;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.save(new Role("READER"));

        User u = new User();
        u.setName("Bob");
        u.setEmail("bob@test.com");
        u.setPassword("hashed");
        u.setProfilePicture("");
        u.setRole(role);
        requester = em.persist(u);

        em.flush();
    }

    private AuthorRequest persistRequest(AuthorRequestStatus status) {
        AuthorRequest request = new AuthorRequest();
        request.setRequester(requester);
        request.setMotivation("I want to write");
        request.setStatus(status);
        return em.persist(request);
    }

    @Test
    void findByRequesterIdAndStatus_pendingExists_returnsRequest() {
        persistRequest(AuthorRequestStatus.PENDING);
        em.flush();

        Optional<AuthorRequest> result = authorRequestRepository
                .findByRequesterIdAndStatus(requester.getId(), AuthorRequestStatus.PENDING);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AuthorRequestStatus.PENDING);
    }

    @Test
    void findByRequesterIdAndStatus_noPendingRequest_returnsEmpty() {
        persistRequest(AuthorRequestStatus.REJECTED);
        em.flush();

        Optional<AuthorRequest> result = authorRequestRepository
                .findByRequesterIdAndStatus(requester.getId(), AuthorRequestStatus.PENDING);

        assertThat(result).isEmpty();
    }

    @Test
    void findByRequesterId_returnsPagedHistoryAllStatuses() {
        persistRequest(AuthorRequestStatus.REJECTED);
        persistRequest(AuthorRequestStatus.PENDING);
        em.flush();

        Page<AuthorRequest> result = authorRequestRepository
                .findByRequesterId(requester.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void existsByRequesterIdAndStatus_pendingExists_returnsTrue() {
        persistRequest(AuthorRequestStatus.PENDING);
        em.flush();

        boolean result = authorRequestRepository
                .existsByRequesterIdAndStatus(requester.getId(), AuthorRequestStatus.PENDING);

        assertThat(result).isTrue();
    }

    @Test
    void existsByRequesterIdAndStatus_noneExists_returnsFalse() {
        boolean result = authorRequestRepository
                .existsByRequesterIdAndStatus(requester.getId(), AuthorRequestStatus.PENDING);

        assertThat(result).isFalse();
    }

    @Test
    void findAllByStatus_withStatusFilter_returnsOnlyMatching() {
        persistRequest(AuthorRequestStatus.PENDING);
        persistRequest(AuthorRequestStatus.REJECTED);
        em.flush();

        Page<AuthorRequest> result = authorRequestRepository
                .findAllByStatus(AuthorRequestStatus.PENDING, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(AuthorRequestStatus.PENDING);
    }

    @Test
    void findAllByStatus_nullStatus_returnsAllRequests() {
        persistRequest(AuthorRequestStatus.PENDING);
        persistRequest(AuthorRequestStatus.REJECTED);
        em.flush();

        Page<AuthorRequest> result = authorRequestRepository
                .findAllByStatus(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void countByStatus_returnsCorrectCount() {
        persistRequest(AuthorRequestStatus.PENDING);
        persistRequest(AuthorRequestStatus.PENDING);
        persistRequest(AuthorRequestStatus.REJECTED);
        em.flush();

        long result = authorRequestRepository.countByStatus(AuthorRequestStatus.PENDING);

        assertThat(result).isEqualTo(2);
    }

    @Test
    void clearResolvedByForUser_nullsOutReferenceOnAllResolvedRequests() {
        Role adminRole = roleRepository.save(new Role("ADMIN"));
        User adminUser = new User();
        adminUser.setName("Admin");
        adminUser.setEmail("admin@test.com");
        adminUser.setPassword("hashed");
        adminUser.setProfilePicture("");
        adminUser.setRole(adminRole);
        em.persist(adminUser);

        AuthorRequest request = persistRequest(AuthorRequestStatus.PENDING);
        request.approve(adminUser, "Welcome aboard");
        em.persist(request);
        em.flush();

        authorRequestRepository.clearResolvedByForUser(adminUser.getId());
        em.flush();
        em.clear();

        AuthorRequest reloaded = authorRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getResolvedBy()).isNull();
    }

    @Test
    void clearClaimedByForUser_nullsOutClaimFieldsForThatAdminOnly() {
        Role adminRole = roleRepository.save(new Role("ADMIN"));
        User adminUser = persistAdmin(adminRole, "admin@test.com");
        User otherAdmin = persistAdmin(adminRole, "otra@test.com");

        AuthorRequest claimedByAdmin = persistRequest(AuthorRequestStatus.PENDING);
        claimedByAdmin.claim(adminUser);
        AuthorRequest claimedByOther = persistRequest(AuthorRequestStatus.PENDING);
        claimedByOther.claim(otherAdmin);
        em.flush();

        authorRequestRepository.clearClaimedByForUser(adminUser.getId());
        em.flush();
        em.clear();

        AuthorRequest cleared = authorRequestRepository.findById(claimedByAdmin.getId()).orElseThrow();
        assertThat(cleared.getClaimedBy()).isNull();
        assertThat(cleared.getClaimedAt()).isNull();

        AuthorRequest untouched = authorRequestRepository.findById(claimedByOther.getId()).orElseThrow();
        assertThat(untouched.getClaimedBy()).isNotNull();
        assertThat(untouched.getClaimedAt()).isNotNull();
    }

    @Test
    void save_versionIncrementsOnEveryUpdate() {
        AuthorRequest request = persistRequest(AuthorRequestStatus.PENDING);
        em.flush();
        Long initialVersion = request.getVersion();

        request.setAdminNote("first note");
        em.flush();

        assertThat(initialVersion).isNotNull();
        assertThat(request.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void saveAndFlush_staleVersion_throwsOptimisticLockingFailure() {
        // Reproduces the two-admins race: both read version N; the first commit bumps
        // the row to N+1, so the second (stale) write must fail instead of silently winning.
        AuthorRequest request = persistRequest(AuthorRequestStatus.PENDING);
        request.setClaimedAt(LocalDateTime.now()); // any column works; claim keeps it realistic
        em.flush();
        Integer id = request.getId();
        em.detach(request); // detached copy still carrying the old version

        AuthorRequest fresh = authorRequestRepository.findById(id).orElseThrow();
        fresh.setAdminNote("resolved by admin A");
        em.flush(); // bumps the version in the database
        em.clear();

        request.setAdminNote("resolved by admin B (stale)");
        assertThatThrownBy(() -> authorRequestRepository.saveAndFlush(request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private User persistAdmin(Role adminRole, String email) {
        User u = new User();
        u.setName(email.substring(0, email.indexOf('@')));
        u.setEmail(email);
        u.setPassword("hashed");
        u.setProfilePicture("");
        u.setRole(adminRole);
        return em.persist(u);
    }

    @Test
    void deleteByRequesterId_removesAllRequestsForRequester() {
        persistRequest(AuthorRequestStatus.REJECTED);
        persistRequest(AuthorRequestStatus.PENDING);
        em.flush();

        authorRequestRepository.deleteByRequesterId(requester.getId());
        em.flush();
        em.clear();

        Page<AuthorRequest> remaining = authorRequestRepository
                .findByRequesterId(requester.getId(), PageRequest.of(0, 10));
        assertThat(remaining).isEmpty();
    }

    @Test
    void findAllByStatus_orderedByCreatedAtAscending() throws InterruptedException {
        AuthorRequest first = persistRequest(AuthorRequestStatus.PENDING);
        em.flush();
        Thread.sleep(10);

        AuthorRequest second = persistRequest(AuthorRequestStatus.PENDING);
        em.flush();

        Page<AuthorRequest> result = authorRequestRepository
                .findAllByStatus(AuthorRequestStatus.PENDING, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(first.getId());
        assertThat(result.getContent().get(1).getId()).isEqualTo(second.getId());
    }
}
