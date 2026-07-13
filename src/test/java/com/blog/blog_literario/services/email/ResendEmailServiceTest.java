package com.blog.blog_literario.services.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.config.properties.EmailVerificationProperties;
import com.blog.blog_literario.config.properties.ResendProperties;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

@ExtendWith(MockitoExtension.class)
class ResendEmailServiceTest {

    @Mock Resend resend;
    @Mock Emails emails;
    @Mock ResendProperties resendProperties;
    @Mock EmailVerificationProperties verificationProperties;

    @InjectMocks ResendEmailService resendEmailService;

    @BeforeEach
    void setUp() {
        given(resend.emails()).willReturn(emails);
        given(resendProperties.from()).willReturn("Marginalia <no-reply@test.dev>");
        given(verificationProperties.tokenExpirationHours()).willReturn(24L);
    }

    @Test
    void sendVerificationEmail_success_sendsOnceWithIdempotencyKey() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendVerificationEmail(
                "alice@test.com", "Alice", "http://front/verify-email?token=abc", "verify-email/10");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        ArgumentCaptor<RequestOptions> requestCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(emails).send(optionsCaptor.capture(), requestCaptor.capture());

        assertThat(optionsCaptor.getValue().getFrom()).isEqualTo("Marginalia <no-reply@test.dev>");
        assertThat(optionsCaptor.getValue().getTo()).containsExactly("alice@test.com");
        assertThat(optionsCaptor.getValue().getHtml()).contains("http://front/verify-email?token=abc");
        assertThat(requestCaptor.getValue().getIdempotencyKey()).isEqualTo("verify-email/10");
    }

    @Test
    void sendVerificationEmail_validationError_doesNotRetryOrThrow() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(new ResendException("Failed to send email: 422 {\"message\":\"Invalid to\"}"));

        resendEmailService.sendVerificationEmail(
                "bad-address", "Alice", "http://front/verify-email?token=abc", "verify-email/11");

        verify(emails, times(1)).send(any(CreateEmailOptions.class), any(RequestOptions.class));
    }

    @Test
    void sendVerificationEmail_rateLimited_retriesAndSucceeds() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(new ResendException("Failed to send email: 429 {\"message\":\"Too many requests\"}"))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendVerificationEmail(
                "alice@test.com", "Alice", "http://front/verify-email?token=abc", "verify-email/12");

        verify(emails, times(2)).send(any(CreateEmailOptions.class), any(RequestOptions.class));
    }

    @Test
    void sendVerificationEmail_transportFailure_retriesWithoutStatusCode() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(new ResendException("connection reset", new RuntimeException()))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendVerificationEmail(
                "alice@test.com", "Alice", "http://front/verify-email?token=abc", "verify-email/13");

        verify(emails, times(2)).send(any(CreateEmailOptions.class), any(RequestOptions.class));
    }

    @Test
    void sendVerificationEmail_escapesUserProvidedNameInHtml() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendVerificationEmail(
                "alice@test.com", "<script>x</script>", "http://front/verify-email?token=abc", "verify-email/14");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(optionsCaptor.capture(), any(RequestOptions.class));
        assertThat(optionsCaptor.getValue().getHtml()).doesNotContain("<script>");
    }
}
