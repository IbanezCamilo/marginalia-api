package com.blog.blog_literario.services.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.config.properties.EmailProperties;
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
    @Mock EmailProperties emailProperties;

    @InjectMocks ResendEmailService resendEmailService;

    @BeforeEach
    void setUp() {
        // lenient: the author-request notification path never reads the verification
        // TTL, and the empty-recipient guard returns before touching any stub — the
        // shared setup must not trip strict stubbing on those tests
        lenient().when(resend.emails()).thenReturn(emails);
        lenient().when(resendProperties.from()).thenReturn("Marginalia <no-reply@test.dev>");
        lenient().when(resendProperties.notificationsFrom()).thenReturn("Marginalia <avisos@test.dev>");
        lenient().when(verificationProperties.tokenExpirationHours()).thenReturn(24L);
        lenient().when(emailProperties.logoUrl()).thenReturn("https://media.test.dev/logo.png");
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
        // The configured logo renders above the greeting, decorative with a brand alt.
        assertThat(optionsCaptor.getValue().getHtml()).contains("src=\"https://media.test.dev/logo.png\"");
        assertThat(optionsCaptor.getValue().getHtml()).contains("alt=\"Marginalia\"");
        // The plain-text part must never depend on the image.
        assertThat(optionsCaptor.getValue().getText()).contains("http://front/verify-email?token=abc");
        assertThat(requestCaptor.getValue().getIdempotencyKey()).isEqualTo("verify-email/10");
    }

    // The 4.4.0 SDK throws raw RuntimeException for non-2xx responses (ResendException
    // only for transport failures), so API-error tests must simulate exactly that.
    @Test
    void sendVerificationEmail_validationError_doesNotRetryOrThrow() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(new RuntimeException("Failed to send email: 422 {\"message\":\"Invalid to\"}"));

        resendEmailService.sendVerificationEmail(
                "bad-address", "Alice", "http://front/verify-email?token=abc", "verify-email/11");

        verify(emails, times(1)).send(any(CreateEmailOptions.class), any(RequestOptions.class));
    }

    @Test
    void sendVerificationEmail_idempotencyConflict_doesNotRetryOrThrow() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(new RuntimeException(
                        "Failed to send email: 409 {\"name\":\"invalid_idempotent_request\"}"));

        resendEmailService.sendVerificationEmail(
                "alice@test.com", "Alice", "http://front/verify-email?token=abc", "verify-email/15");

        verify(emails, times(1)).send(any(CreateEmailOptions.class), any(RequestOptions.class));
    }

    @Test
    void sendVerificationEmail_rateLimited_retriesAndSucceeds() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(new RuntimeException("Failed to send email: 429 {\"message\":\"Too many requests\"}"))
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

    // ─── Author request notification ───────────────────────────────────────────

    @Test
    void sendAuthorRequestNotification_success_sendsOnceToAllAdminsWithIdempotencyKey() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendAuthorRequestNotification(
                List.of("admin@test.com", "owner@test.com"), "Reader", "reader@test.com",
                "I want to write", "http://front/user/solicitudes", "author-request/7");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        ArgumentCaptor<RequestOptions> requestCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(emails).send(optionsCaptor.capture(), requestCaptor.capture());

        // Staff notifications use their own sender, not the account-email address
        assertThat(optionsCaptor.getValue().getFrom()).isEqualTo("Marginalia <avisos@test.dev>");
        assertThat(optionsCaptor.getValue().getTo()).containsExactly("admin@test.com", "owner@test.com");
        assertThat(optionsCaptor.getValue().getSubject()).isEqualTo("Nueva solicitud de autoría en Marginalia");
        assertThat(optionsCaptor.getValue().getHtml()).contains("Reader");
        assertThat(optionsCaptor.getValue().getHtml()).contains("reader@test.com");
        assertThat(optionsCaptor.getValue().getHtml()).contains("I want to write");
        assertThat(optionsCaptor.getValue().getHtml()).contains("http://front/user/solicitudes");
        // The plain-text part must never depend on the HTML rendering.
        assertThat(optionsCaptor.getValue().getText()).contains("http://front/user/solicitudes");
        assertThat(optionsCaptor.getValue().getText()).contains("I want to write");
        assertThat(requestCaptor.getValue().getIdempotencyKey()).isEqualTo("author-request/7");
    }

    @Test
    void sendAuthorRequestNotification_escapesUserProvidedNameAndMotivationInHtml() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendAuthorRequestNotification(
                List.of("admin@test.com"), "<script>x</script>", "reader@test.com",
                "<img src=x onerror=alert(1)>", "http://front/user/solicitudes", "author-request/8");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(optionsCaptor.capture(), any(RequestOptions.class));
        assertThat(optionsCaptor.getValue().getHtml()).doesNotContain("<script>");
        assertThat(optionsCaptor.getValue().getHtml()).doesNotContain("<img src=x");
    }

    @Test
    void sendAuthorRequestNotification_nullMotivation_rendersFallback() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendAuthorRequestNotification(
                List.of("admin@test.com"), "Reader", "reader@test.com",
                null, "http://front/user/solicitudes", "author-request/9");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(optionsCaptor.capture(), any(RequestOptions.class));
        assertThat(optionsCaptor.getValue().getText()).contains("(sin motivación)");
        assertThat(optionsCaptor.getValue().getHtml()).contains("(sin motivaci");
    }

    @Test
    void sendAuthorRequestNotification_emptyRecipients_sendsNothing() {
        resendEmailService.sendAuthorRequestNotification(
                List.of(), "Reader", "reader@test.com",
                "I want to write", "http://front/user/solicitudes", "author-request/10");

        verifyNoInteractions(emails);
    }

    @Test
    void sendAuthorRequestNotification_rateLimited_retriesAndSucceeds() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(new RuntimeException("Failed to send email: 429 {\"message\":\"Too many requests\"}"))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendAuthorRequestNotification(
                List.of("admin@test.com"), "Reader", "reader@test.com",
                "I want to write", "http://front/user/solicitudes", "author-request/11");

        verify(emails, times(2)).send(any(CreateEmailOptions.class), any(RequestOptions.class));
    }

    @Test
    void sendAuthorRequestNotification_validationError_doesNotRetryOrThrow() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(new RuntimeException("Failed to send email: 422 {\"message\":\"Invalid to\"}"));

        resendEmailService.sendAuthorRequestNotification(
                List.of("bad-address"), "Reader", "reader@test.com",
                "I want to write", "http://front/user/solicitudes", "author-request/12");

        verify(emails, times(1)).send(any(CreateEmailOptions.class), any(RequestOptions.class));
    }

    @Test
    void sendAuthorRequestNotification_serverErrorsExhaustAllAttemptsWithoutThrowing() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willThrow(new RuntimeException("Failed to send email: 500 {\"message\":\"Internal error\"}"));

        assertThatCode(() -> resendEmailService.sendAuthorRequestNotification(
                List.of("admin@test.com"), "Reader", "reader@test.com",
                "I want to write", "http://front/user/solicitudes", "author-request/13"))
                .doesNotThrowAnyException();

        verify(emails, times(3)).send(any(CreateEmailOptions.class), any(RequestOptions.class));
    }

    // ─── Post moderation notification ──────────────────────────────────────────

    @Test
    void sendPostModerationNotification_published_buildsSubjectBodyAndIdempotencyKey() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendPostModerationNotification(
                "alice@test.com", "Alice", "Mi <cuento>",
                com.blog.blog_literario.model.PostStatus.DRAFT,
                com.blog.blog_literario.model.PostStatus.PUBLISHED,
                null, "http://front/user/posts", "post-moderation/10/abc");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        ArgumentCaptor<RequestOptions> requestCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(emails).send(optionsCaptor.capture(), requestCaptor.capture());

        assertThat(optionsCaptor.getValue().getFrom()).isEqualTo("Marginalia <avisos@test.dev>");
        assertThat(optionsCaptor.getValue().getTo()).containsExactly("alice@test.com");
        assertThat(optionsCaptor.getValue().getSubject()).isEqualTo("Tu post fue publicado en Marginalia");
        // User-authored title must be escaped in the HTML part.
        assertThat(optionsCaptor.getValue().getHtml()).contains("Mi &lt;cuento&gt;");
        assertThat(optionsCaptor.getValue().getHtml()).doesNotContain("Mi <cuento>");
        assertThat(optionsCaptor.getValue().getHtml()).contains("http://front/user/posts");
        // Old and new status names appear in both parts.
        assertThat(optionsCaptor.getValue().getHtml()).contains("Borrador").contains("Publicado");
        assertThat(optionsCaptor.getValue().getText()).contains("Borrador").contains("Publicado");
        assertThat(optionsCaptor.getValue().getText()).contains("http://front/user/posts");
        assertThat(requestCaptor.getValue().getIdempotencyKey()).isEqualTo("post-moderation/10/abc");
    }

    @Test
    void sendPostModerationNotification_rejectedWithNote_includesEscapedNote() throws ResendException {
        given(emails.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .willReturn(new CreateEmailResponse());

        resendEmailService.sendPostModerationNotification(
                "alice@test.com", "Alice", "Mi cuento",
                com.blog.blog_literario.model.PostStatus.PUBLISHED,
                com.blog.blog_literario.model.PostStatus.REJECTED,
                "Revisa <b>ortografía</b>", "http://front/user/posts", "post-moderation/11/def");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emails).send(optionsCaptor.capture(), any(RequestOptions.class));

        assertThat(optionsCaptor.getValue().getSubject()).isEqualTo("Tu post necesita cambios");
        assertThat(optionsCaptor.getValue().getHtml()).contains("Revisa &lt;b&gt;ortograf");
        assertThat(optionsCaptor.getValue().getText()).contains("Revisa <b>ortografía</b>");
    }
}
