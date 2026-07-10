package com.blog.blog_literario.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.blog.blog_literario.config.properties.OwnerProperties;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock RoleRepository roleRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock OwnerProperties ownerProperties;

    @InjectMocks DataInitializer dataInitializer;

    @Test
    void run_noExistingRoles_createsAllFiveRoles() throws Exception {
        // roleInitializer's own lookup for OWNER must see it as missing (so it's
        // created), but ownerInitializer's later lookup must see it as present
        // (so it can assign it to the seed user) — same mocked call, two answers.
        given(roleRepository.findByName(any())).willReturn(Optional.empty());
        given(roleRepository.findByName(Role.OWNER))
                .willReturn(Optional.empty(), Optional.of(new Role(5, Role.OWNER)));
        given(ownerProperties.email()).willReturn("owner@test.com");
        given(userRepository.findByEmail("owner@test.com")).willReturn(Optional.empty());
        given(ownerProperties.password()).willReturn("password123");
        given(passwordEncoder.encode("password123")).willReturn("encoded-hash");

        dataInitializer.run();

        verify(roleRepository).save(argThat((Role r) -> Role.READER.equals(r.getName())));
        verify(roleRepository).save(argThat((Role r) -> Role.AUTHOR.equals(r.getName())));
        verify(roleRepository).save(argThat((Role r) -> Role.MODERATOR.equals(r.getName())));
        verify(roleRepository).save(argThat((Role r) -> Role.ADMIN.equals(r.getName())));
        verify(roleRepository).save(argThat((Role r) -> Role.OWNER.equals(r.getName())));
        verify(roleRepository, times(5)).save(any());
    }

    @Test
    void run_seedUserAbsent_createsNewOwnerUser() throws Exception {
        given(roleRepository.findByName(any())).willReturn(Optional.empty());
        given(roleRepository.findByName(Role.OWNER))
                .willReturn(Optional.empty(), Optional.of(new Role(5, Role.OWNER)));
        given(ownerProperties.email()).willReturn("owner@test.com");
        given(userRepository.findByEmail("owner@test.com")).willReturn(Optional.empty());
        given(ownerProperties.password()).willReturn("password123");
        given(passwordEncoder.encode("password123")).willReturn("encoded-hash");

        dataInitializer.run();

        verify(userRepository).save(argThat((User user) ->
                "owner@test.com".equals(user.getEmail())
                        && "encoded-hash".equals(user.getPassword())
                        && Role.OWNER.equals(user.getRole().getName())));
    }

    @Test
    void run_seedUserExistsAsAdmin_upgradesToOwnerAndIncrementsTokenVersion() throws Exception {
        User existingAdmin = new User(1, "Admin", "owner@test.com", new Role(1, Role.ADMIN));
        given(roleRepository.findByName(any())).willReturn(Optional.empty());
        given(roleRepository.findByName(Role.OWNER)).willReturn(Optional.of(new Role(2, Role.OWNER)));
        given(ownerProperties.email()).willReturn("owner@test.com");
        given(userRepository.findByEmail("owner@test.com")).willReturn(Optional.of(existingAdmin));

        dataInitializer.run();

        assertThat(existingAdmin.getRole().getName()).isEqualTo(Role.OWNER);
        assertThat(existingAdmin.getTokenVersion()).isEqualTo(1);
        verify(userRepository).save(existingAdmin);
    }

    @Test
    void run_seedUserAlreadyOwner_doesNotResaveOrIncrementTokenVersion() throws Exception {
        User existingOwner = new User(1, "Owner", "owner@test.com", new Role(1, Role.OWNER));
        given(roleRepository.findByName(any())).willReturn(Optional.empty());
        given(roleRepository.findByName(Role.OWNER)).willReturn(Optional.of(new Role(1, Role.OWNER)));
        given(ownerProperties.email()).willReturn("owner@test.com");
        given(userRepository.findByEmail("owner@test.com")).willReturn(Optional.of(existingOwner));

        dataInitializer.run();

        assertThat(existingOwner.getTokenVersion()).isEqualTo(0);
        verify(userRepository, never()).save(any());
    }
}
