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
import com.blog.blog_literario.events.EmailChangeCompleted;
import com.blog.blog_literario.events.EmailChangeRequested;

@ExtendWith(MockitoExtension.class)
class EmailChangeNotificationListenerTest {

    @Mock EmailService emailService;

    private EmailChangeNotificationListener listener() {
        return new EmailChangeNotificationListener(emailService, new FrontendProperties("http://localhost:5173"));
    }

    @Test
    void onEmailChangeRequested_sendsConfirmToNewAddressAndCancelToOld() {
        var event = new EmailChangeRequested(
                "new@test.com", "old@test.com", "Alice", "confirm-raw", "cancel-raw", "email-change/hash");

        listener().onEmailChangeRequested(event);

        // Confirm link → new address; distinct per-email idempotency key.
        verify(emailService).sendEmailChangeConfirmation(
                "new@test.com", "Alice",
                "http://localhost:5173/confirm-email-change?token=confirm-raw",
                "email-change/hash/confirm");
        // Cancel link → old address; the notice also surfaces the requested new address.
        verify(emailService).sendEmailChangeNotice(
                "old@test.com", "Alice", "new@test.com",
                "http://localhost:5173/cancel-email-change?token=cancel-raw",
                "email-change/hash/cancel");
    }

    @Test
    void onEmailChangeCompleted_notifiesOldAddress() {
        var event = new EmailChangeCompleted("old@test.com", "new@test.com", "Alice", "email-change-done/hash");

        listener().onEmailChangeCompleted(event);

        verify(emailService).sendEmailChangeCompletedNotice(
                "old@test.com", "Alice", "new@test.com", "email-change-done/hash");
    }

    @Test
    void onEmailChangeRequested_swallowsSendFailures() {
        willThrow(new RuntimeException("boom"))
                .given(emailService).sendEmailChangeConfirmation(any(), any(), any(), any());
        var event = new EmailChangeRequested(
                "new@test.com", "old@test.com", "Alice", "confirm-raw", "cancel-raw", "email-change/hash");

        assertThatCode(() -> listener().onEmailChangeRequested(event)).doesNotThrowAnyException();
    }
}
