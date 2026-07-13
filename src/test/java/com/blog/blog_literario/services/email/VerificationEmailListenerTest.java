package com.blog.blog_literario.services.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.events.VerificationEmailRequested;

@ExtendWith(MockitoExtension.class)
class VerificationEmailListenerTest {

    @Mock EmailService emailService;

    @Test
    void onVerificationEmailRequested_buildsFrontendUrlAndDelegates() {
        VerificationEmailListener listener = new VerificationEmailListener(
                emailService, new FrontendProperties("http://localhost:5173"));
        var event = new VerificationEmailRequested("alice@test.com", "Alice", "raw-token", "verify-email/10");

        listener.onVerificationEmailRequested(event);

        verify(emailService).sendVerificationEmail(
                "alice@test.com", "Alice",
                "http://localhost:5173/verify-email?token=raw-token",
                "verify-email/10");
    }

    @Test
    void onVerificationEmailRequested_swallowsSendFailures() {
        VerificationEmailListener listener = new VerificationEmailListener(
                emailService, new FrontendProperties("http://localhost:5173"));
        willThrow(new RuntimeException("boom"))
                .given(emailService).sendVerificationEmail(any(), any(), any(), any());
        var event = new VerificationEmailRequested("alice@test.com", "Alice", "raw-token", "verify-email/10");

        assertThatCode(() -> listener.onVerificationEmailRequested(event))
                .doesNotThrowAnyException();
    }
}
