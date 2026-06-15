package com.blog.blog_literario.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserValidatorTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserValidator userValidator;

    @Test
    void validateEmailUniqueness_emailExists_throwsUserAlreadyExistsException() {
        User existing = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> userValidator.validateEmailUniqueness("alice@test.com"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("alice@test.com");
    }

    @Test
    void validateEmailUniqueness_emailNotFound_doesNotThrow() {
        given(userRepository.findByEmail("new@test.com")).willReturn(Optional.empty());

        assertThatCode(() -> userValidator.validateEmailUniqueness("new@test.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void sanitizeInput_trimsWhitespace_andNullReturnsEmptyString() {
        assertThat(userValidator.sanitizeInput("  hello  ")).isEqualTo("hello");
        assertThat(userValidator.sanitizeInput(null)).isEqualTo("");
    }

    @Test
    void validateAndSanitizeEmail_validNewEmail_returnsLowercasedTrimmed() {
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.empty());

        String result = userValidator.validateAndSanitizeEmail("  Alice@Test.com  ");

        assertThat(result).isEqualTo("alice@test.com");
    }

    @Test
    void validateAndSanitizeEmail_duplicateEmail_throws() {
        User existing = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> userValidator.validateAndSanitizeEmail("  Alice@Test.com  "))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void sanitizeEmail_doesNotCheckUniqueness_justNormalizes() {
        String result = userValidator.sanitizeEmail("  Alice@Test.com  ");

        assertThat(result).isEqualTo("alice@test.com");
        verifyNoInteractions(userRepository);
    }

    @Test
    void validateAndSanitizeName_blankAfterTrim_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> userValidator.validateAndSanitizeName("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateAndSanitizeName_validName_returnsTrimmed() {
        assertThat(userValidator.validateAndSanitizeName("  Alice  ")).isEqualTo("Alice");
    }
}
