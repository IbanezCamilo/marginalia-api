package com.blog.blog_literario.services.email;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.blog.blog_literario.config.properties.EmailVerificationProperties;
import com.blog.blog_literario.config.properties.ResendProperties;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.model.CreateEmailOptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resend implementation of {@link EmailService}, active only when
 * {@code email.provider=resend}.
 *
 * <p>Retries only rate limits (429) and Resend-side errors (5xx / transport failures)
 * with exponential backoff; validation errors (4xx) are never retried. The idempotency
 * key is sent on every attempt so a network retry cannot duplicate the email. Delivery
 * failures are logged, never propagated — the resend endpoint is the recovery path.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "resend")
@RequiredArgsConstructor
public class ResendEmailService implements EmailService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;
    private static final Pattern STATUS_IN_MESSAGE = Pattern.compile("^Failed to send email: (\\d{3})");

    private final Resend resend;
    private final ResendProperties resendProperties;
    private final EmailVerificationProperties verificationProperties;

    @Override
    public void sendVerificationEmail(String to, String userName, String verificationUrl, String idempotencyKey) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(resendProperties.from())
                .to(to)
                .subject("Confirma tu correo electrónico en Marginalia")
                .html(buildHtmlBody(userName, verificationUrl))
                .text(buildTextBody(userName, verificationUrl))
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                resend.emails().send(params, options);
                return;
            } catch (ResendException e) {
                Integer status = extractStatusCode(e);
                if (!isRetryable(status) || attempt == MAX_ATTEMPTS) {
                    log.error("Failed to send verification email to {} (status {}): {}",
                            to, status, e.getMessage());
                    return;
                }
                log.warn("Retryable error sending verification email to {} (status {}, attempt {}/{})",
                        to, status, attempt, MAX_ATTEMPTS);
                if (!backOff(attempt)) {
                    return;
                }
            }
        }
    }

    /** A null status means the request never reached Resend (transport failure) — retryable. */
    private boolean isRetryable(Integer status) {
        return status == null || status == 429 || status >= 500;
    }

    /**
     * The 4.4.0 SDK's {@link ResendException} carries no status field; API failures
     * arrive as {@code "Failed to send email: <status> <body>"}, so the code is
     * recovered from the message. Returns null when no status is present.
     */
    private Integer extractStatusCode(ResendException e) {
        if (e.getMessage() == null) {
            return null;
        }
        Matcher matcher = STATUS_IN_MESSAGE.matcher(e.getMessage());
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** Sleeps 1s, 2s, 4s... Returns false if interrupted, so the caller stops retrying. */
    private boolean backOff(int attempt) {
        try {
            Thread.sleep(INITIAL_BACKOFF_MS << (attempt - 1));
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String buildHtmlBody(String userName, String verificationUrl) {
        String safeName = HtmlUtils.htmlEscape(userName);
        String safeUrl = HtmlUtils.htmlEscape(verificationUrl);
        long hours = verificationProperties.tokenExpirationHours();
        return """
                <div style="font-family: Georgia, serif; max-width: 520px; margin: 0 auto; color: #1a1a1a;">
                  <h1 style="font-size: 22px;">Marginalia</h1>
                  <p>Hola %s,</p>
                  <p>Gracias por registrarte en Marginalia. Confirma tu correo electrónico haciendo clic en el siguiente botón:</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background: #1a1a1a; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 4px;">Confirmar correo</a>
                  </p>
                  <p>O copia y pega este enlace en tu navegador:</p>
                  <p style="word-break: break-all; font-size: 13px; color: #555555;">%s</p>
                  <p style="font-size: 13px; color: #555555;">Este enlace caducará en %d horas. Si no creaste esta cuenta, puedes ignorar este mensaje.</p>
                </div>
                """.formatted(safeName, safeUrl, safeUrl, hours);
    }

    private String buildTextBody(String userName, String verificationUrl) {
        return """
                Hola %s,

                Gracias por registrarte en Marginalia. Confirma tu correo electrónico abriendo este enlace:

                %s

                Este enlace caducará en %d horas. Si no creaste esta cuenta, puedes ignorar este mensaje.
                """.formatted(userName, verificationUrl, verificationProperties.tokenExpirationHours());
    }
}
