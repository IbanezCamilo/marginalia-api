package com.blog.blog_literario.services.email;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.blog.blog_literario.config.properties.EmailProperties;
import com.blog.blog_literario.config.properties.EmailVerificationProperties;
import com.blog.blog_literario.config.properties.ResendProperties;
import com.blog.blog_literario.model.PostStatus;
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
    private final EmailProperties emailProperties;

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

        sendWithRetry(params, options, "verification email to " + to);
    }

    @Override
    public void sendAuthorRequestNotification(List<String> to, String requesterName, String requesterEmail,
            String motivation, String adminPanelUrl, String idempotencyKey) {
        if (to == null || to.isEmpty()) {
            log.warn("No admin recipients for author request notification ({}); nothing sent", idempotencyKey);
            return;
        }

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(resendProperties.notificationsFrom())
                .to(to)
                .subject("Nueva solicitud de autoría en Marginalia")
                .html(buildAuthorRequestHtmlBody(requesterName, requesterEmail, motivation, adminPanelUrl))
                .text(buildAuthorRequestTextBody(requesterName, requesterEmail, motivation, adminPanelUrl))
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        sendWithRetry(params, options, "author request notification to " + to);
    }

    @Override
    public void sendPostModerationNotification(String to, String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote,
            String postsUrl, String idempotencyKey) {

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(resendProperties.notificationsFrom())
                .to(to)
                .subject(moderationSubject(newStatus))
                .html(buildModerationHtmlBody(authorName, postTitle, previousStatus, newStatus,
                        moderationNote, postsUrl))
                .text(buildModerationTextBody(authorName, postTitle, previousStatus, newStatus,
                        moderationNote, postsUrl))
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        sendWithRetry(params, options, "post moderation notification to " + to);
    }

    @Override
    public void sendEmailChangeConfirmation(String to, String userName, String confirmUrl, String idempotencyKey) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(resendProperties.from())
                .to(to)
                .subject("Confirma tu nuevo correo en Marginalia")
                .html(buildEmailChangeConfirmationHtmlBody(userName, confirmUrl))
                .text(buildEmailChangeConfirmationTextBody(userName, confirmUrl))
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        sendWithRetry(params, options, "email change confirmation to " + to);
    }

    @Override
    public void sendEmailChangeNotice(String to, String userName, String newEmail, String cancelUrl, String idempotencyKey) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(resendProperties.from())
                .to(to)
                .subject("Se solicitó un cambio de correo en tu cuenta")
                .html(buildEmailChangeNoticeHtmlBody(userName, newEmail, cancelUrl))
                .text(buildEmailChangeNoticeTextBody(userName, newEmail, cancelUrl))
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        sendWithRetry(params, options, "email change notice to " + to);
    }

    @Override
    public void sendEmailChangeCompletedNotice(String to, String userName, String newEmail, String idempotencyKey) {
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(resendProperties.from())
                .to(to)
                .subject("Tu correo de Marginalia fue cambiado")
                .html(buildEmailChangeCompletedHtmlBody(userName, newEmail))
                .text(buildEmailChangeCompletedTextBody(userName, newEmail))
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        sendWithRetry(params, options, "email change completed notice to " + to);
    }

    /** Subject/headline copy is driven by the post's NEW status. */
    private String moderationSubject(PostStatus newStatus) {
        return switch (newStatus) {
            case PUBLISHED -> "Tu post fue publicado en Marginalia";
            case REJECTED  -> "Tu post necesita cambios";
            case ARCHIVED  -> "Tu post fue archivado";
            case DRAFT     -> "Tu post volvió a borrador";
        };
    }

    /**
     * Author notification for a moderation status change, in the same
     * inline-styled single-column format as the other templates. The author's
     * name, post title, and moderation note are user-authored — always escaped.
     */
    private String buildModerationHtmlBody(String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote, String postsUrl) {
        String safeName = HtmlUtils.htmlEscape(authorName);
        String safeTitle = HtmlUtils.htmlEscape(postTitle);
        String safeUrl = HtmlUtils.htmlEscape(postsUrl);
        String safeLogoUrl = HtmlUtils.htmlEscape(emailProperties.logoUrl());
        String subject = HtmlUtils.htmlEscape(moderationSubject(newStatus));

        String noteBlock = (moderationNote == null || moderationNote.isBlank()) ? "" : """
                <p style="margin: 0 0 8px 0; font-size: 13px; text-transform: uppercase; letter-spacing: 1px; color: #a8a29e;">Nota de moderaci&oacute;n</p>
                <p style="margin: 0 0 28px 0; padding: 12px 16px; border-left: 3px solid #e7e5e4; font-size: 15px; line-height: 1.6; color: #57534e; font-style: italic;">%s</p>
                """.formatted(HtmlUtils.htmlEscape(moderationNote));

        return """
                <div style="display: none; max-height: 0; overflow: hidden; mso-hide: all;">%s</div>
                <div style="background-color: #faf8f5; padding: 40px 16px; font-family: Georgia, 'Times New Roman', serif;">
                  <div style="max-width: 520px; margin: 0 auto;">
                    <img src="%s" alt="Marginalia" width="120" style="display: block; margin: 0 auto 28px auto; border: 0;">
                    <p style="margin: 0 0 6px 0; text-align: center; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; color: #a8a29e;">&mdash; Moderaci&oacute;n &mdash;</p>
                    <h1 style="margin: 0 0 24px 0; text-align: center; font-size: 26px; font-weight: normal; color: #1c1917;">%s</h1>
                    <p style="margin: 0 0 8px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Hola %s,</p>
                    <p style="margin: 0 0 28px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Tu post <strong>%s</strong> cambi&oacute; de estado: %s &rarr; <strong>%s</strong>.</p>
                    %s<p style="margin: 0 0 28px 0; text-align: center;">
                      <a href="%s" style="display: inline-block; background-color: #be163d; color: #ffffff; padding: 12px 28px; font-size: 15px; text-decoration: none; border-radius: 4px;">Ver mis posts</a>
                    </p>
                    <div style="border-top: 1px solid #e7e5e4; margin: 0 0 16px 0;"></div>
                    <p style="margin: 0; font-size: 12px; line-height: 1.6; color: #a8a29e;">Recibes este mensaje porque eres autor en Marginalia. Puedes desactivar estos correos en Configuraci&oacute;n.</p>
                  </div>
                </div>
                """.formatted(subject, safeLogoUrl, subject, safeName, safeTitle,
                        previousStatus.getDisplayName(), newStatus.getDisplayName(),
                        noteBlock, safeUrl);
    }

    private String buildModerationTextBody(String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote, String postsUrl) {
        String noteBlock = (moderationNote == null || moderationNote.isBlank()) ? "" : """

                Nota de moderación:
                %s
                """.formatted(moderationNote);
        return """
                Hola %s,

                Tu post "%s" cambió de estado: %s → %s.
                %s
                Ver tus posts:

                %s

                Recibes este mensaje porque eres autor en Marginalia. Puedes desactivar estos correos en Configuración.
                """.formatted(authorName, postTitle,
                        previousStatus.getDisplayName(), newStatus.getDisplayName(),
                        noteBlock, postsUrl);
    }

    /**
     * Sends with up to {@code MAX_ATTEMPTS} tries and exponential backoff.
     * Only rate limits (429) and Resend-side errors (5xx / transport failures) are
     * retried; the idempotency key travels on every attempt so a retry cannot
     * duplicate the email. Failures are logged, never propagated.
     */
    private void sendWithRetry(CreateEmailOptions params, RequestOptions options, String description) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                resend.emails().send(params, options);
                return;
            } catch (Exception e) {
                Integer status = extractStatusCode(e);
                if (!isRetryable(status) || attempt == MAX_ATTEMPTS) {
                    log.error("Failed to send {} (status {}): {}",
                            description, status, e.getMessage());
                    return;
                }
                log.warn("Retryable error sending {} (status {}, attempt {}/{})",
                        description, status, attempt, MAX_ATTEMPTS);
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
     * The 4.4.0 SDK throws a raw {@link RuntimeException} with message
     * {@code "Failed to send email: <status> <body>"} for non-2xx responses, and
     * {@link ResendException} only for transport failures — so the status code is
     * recovered from the message. Returns null when no status is present.
     */
    private Integer extractStatusCode(Exception e) {
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

    /**
     * Single-column, inline-styled HTML that renders correctly in conservative email
     * clients (no external CSS, no flexbox/grid, no web fonts). The logo is decorative:
     * with images blocked, its alt text carries the brand name and the verification
     * link remains available as button and plain text.
     */
    private String buildHtmlBody(String userName, String verificationUrl) {
        String safeName = HtmlUtils.htmlEscape(userName);
        String safeUrl = HtmlUtils.htmlEscape(verificationUrl);
        String safeLogoUrl = HtmlUtils.htmlEscape(emailProperties.logoUrl());
        long hours = verificationProperties.tokenExpirationHours();
        return """
                <div style="display: none; max-height: 0; overflow: hidden; mso-hide: all;">Confirma tu correo para activar tu cuenta en Marginalia.</div>
                <div style="background-color: #faf8f5; padding: 40px 16px; font-family: Georgia, 'Times New Roman', serif;">
                  <div style="max-width: 520px; margin: 0 auto;">
                    <img src="%s" alt="Marginalia" width="120" style="display: block; margin: 0 auto 28px auto; border: 0;">
                    <p style="margin: 0 0 6px 0; text-align: center; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; color: #a8a29e;">&mdash; Verificaci&oacute;n &mdash;</p>
                    <h1 style="margin: 0 0 24px 0; text-align: center; font-size: 26px; font-weight: normal; color: #1c1917;">Confirma tu correo</h1>
                    <p style="margin: 0 0 8px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Hola %s,</p>
                    <p style="margin: 0 0 28px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Tu cuenta en Marginalia est&aacute; casi lista. Confirma tu correo electr&oacute;nico para empezar a leer:</p>
                    <p style="margin: 0 0 28px 0; text-align: center;">
                      <a href="%s" style="display: inline-block; background-color: #be163d; color: #ffffff; padding: 12px 28px; font-size: 15px; text-decoration: none; border-radius: 4px;">Confirmar correo</a>
                    </p>
                    <p style="margin: 0 0 4px 0; font-size: 13px; line-height: 1.6; color: #78716c;">O copia y pega este enlace en tu navegador:</p>
                    <p style="margin: 0 0 28px 0; font-size: 13px; line-height: 1.6; color: #78716c; word-break: break-all;"><a href="%s" style="color: #be163d;">%s</a></p>
                    <div style="border-top: 1px solid #e7e5e4; margin: 0 0 16px 0;"></div>
                    <p style="margin: 0; font-size: 12px; line-height: 1.6; color: #a8a29e;">Este enlace caducar&aacute; en %d horas. Si no creaste esta cuenta, puedes ignorar este mensaje.</p>
                  </div>
                </div>
                """.formatted(safeLogoUrl, safeName, safeUrl, safeUrl, safeUrl, hours);
    }

    private String buildTextBody(String userName, String verificationUrl) {
        return """
                Hola %s,

                Tu cuenta en Marginalia está casi lista. Confirma tu correo electrónico abriendo este enlace:

                %s

                Este enlace caducará en %d horas. Si no creaste esta cuenta, puedes ignorar este mensaje.
                """.formatted(userName, verificationUrl, verificationProperties.tokenExpirationHours());
    }

    /**
     * Admin notification for a new author request, in the same inline-styled
     * single-column format as the verification email. The requester's name,
     * email, and motivation are user-authored — always HTML-escaped.
     */
    private String buildAuthorRequestHtmlBody(String requesterName, String requesterEmail,
            String motivation, String adminPanelUrl) {
        String safeName = HtmlUtils.htmlEscape(requesterName);
        String safeEmail = HtmlUtils.htmlEscape(requesterEmail);
        String safeMotivation = HtmlUtils.htmlEscape(motivationOrFallback(motivation));
        String safeUrl = HtmlUtils.htmlEscape(adminPanelUrl);
        String safeLogoUrl = HtmlUtils.htmlEscape(emailProperties.logoUrl());
        return """
                <div style="display: none; max-height: 0; overflow: hidden; mso-hide: all;">%s ha solicitado convertirse en autor en Marginalia.</div>
                <div style="background-color: #faf8f5; padding: 40px 16px; font-family: Georgia, 'Times New Roman', serif;">
                  <div style="max-width: 520px; margin: 0 auto;">
                    <img src="%s" alt="Marginalia" width="120" style="display: block; margin: 0 auto 28px auto; border: 0;">
                    <p style="margin: 0 0 6px 0; text-align: center; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; color: #a8a29e;">&mdash; Solicitud de autor&iacute;a &mdash;</p>
                    <h1 style="margin: 0 0 24px 0; text-align: center; font-size: 26px; font-weight: normal; color: #1c1917;">Nueva solicitud de autor&iacute;a</h1>
                    <p style="margin: 0 0 8px 0; font-size: 15px; line-height: 1.6; color: #57534e;"><strong>%s</strong> (%s) ha solicitado convertirse en autor.</p>
                    <p style="margin: 0 0 8px 0; font-size: 13px; text-transform: uppercase; letter-spacing: 1px; color: #a8a29e;">Motivaci&oacute;n</p>
                    <p style="margin: 0 0 28px 0; padding: 12px 16px; border-left: 3px solid #e7e5e4; font-size: 15px; line-height: 1.6; color: #57534e; font-style: italic;">%s</p>
                    <p style="margin: 0 0 28px 0; text-align: center;">
                      <a href="%s" style="display: inline-block; background-color: #be163d; color: #ffffff; padding: 12px 28px; font-size: 15px; text-decoration: none; border-radius: 4px;">Revisar solicitud</a>
                    </p>
                    <div style="border-top: 1px solid #e7e5e4; margin: 0 0 16px 0;"></div>
                    <p style="margin: 0; font-size: 12px; line-height: 1.6; color: #a8a29e;">Recibes este mensaje porque eres administrador de Marginalia.</p>
                  </div>
                </div>
                """.formatted(safeName, safeLogoUrl, safeName, safeEmail, safeMotivation, safeUrl);
    }

    private String buildAuthorRequestTextBody(String requesterName, String requesterEmail,
            String motivation, String adminPanelUrl) {
        return """
                %s (%s) ha solicitado convertirse en autor en Marginalia.

                Motivación:
                %s

                Revisa la solicitud aquí:

                %s

                Recibes este mensaje porque eres administrador de Marginalia.
                """.formatted(requesterName, requesterEmail, motivationOrFallback(motivation), adminPanelUrl);
    }

    /** Motivation is optional; a fallback keeps the template (and htmlEscape) null-safe. */
    private String motivationOrFallback(String motivation) {
        return (motivation == null || motivation.isBlank()) ? "(sin motivación)" : motivation;
    }

    /** Confirmation email to the NEW address; clicking the link commits the change. */
    private String buildEmailChangeConfirmationHtmlBody(String userName, String confirmUrl) {
        String safeName = HtmlUtils.htmlEscape(userName);
        String safeUrl = HtmlUtils.htmlEscape(confirmUrl);
        String safeLogoUrl = HtmlUtils.htmlEscape(emailProperties.logoUrl());
        long hours = verificationProperties.tokenExpirationHours();
        return """
                <div style="display: none; max-height: 0; overflow: hidden; mso-hide: all;">Confirma tu nuevo correo para completar el cambio en Marginalia.</div>
                <div style="background-color: #faf8f5; padding: 40px 16px; font-family: Georgia, 'Times New Roman', serif;">
                  <div style="max-width: 520px; margin: 0 auto;">
                    <img src="%s" alt="Marginalia" width="120" style="display: block; margin: 0 auto 28px auto; border: 0;">
                    <p style="margin: 0 0 6px 0; text-align: center; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; color: #a8a29e;">&mdash; Cambio de correo &mdash;</p>
                    <h1 style="margin: 0 0 24px 0; text-align: center; font-size: 26px; font-weight: normal; color: #1c1917;">Confirma tu nuevo correo</h1>
                    <p style="margin: 0 0 8px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Hola %s,</p>
                    <p style="margin: 0 0 28px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Solicitaste usar esta direcci&oacute;n como el correo de tu cuenta. Conf&iacute;rmala para completar el cambio. Hasta entonces, tu correo actual sigue activo.</p>
                    <p style="margin: 0 0 28px 0; text-align: center;">
                      <a href="%s" style="display: inline-block; background-color: #be163d; color: #ffffff; padding: 12px 28px; font-size: 15px; text-decoration: none; border-radius: 4px;">Confirmar nuevo correo</a>
                    </p>
                    <p style="margin: 0 0 4px 0; font-size: 13px; line-height: 1.6; color: #78716c;">O copia y pega este enlace en tu navegador:</p>
                    <p style="margin: 0 0 28px 0; font-size: 13px; line-height: 1.6; color: #78716c; word-break: break-all;"><a href="%s" style="color: #be163d;">%s</a></p>
                    <div style="border-top: 1px solid #e7e5e4; margin: 0 0 16px 0;"></div>
                    <p style="margin: 0; font-size: 12px; line-height: 1.6; color: #a8a29e;">Este enlace caducar&aacute; en %d horas. Si no solicitaste este cambio, puedes ignorar este mensaje.</p>
                  </div>
                </div>
                """.formatted(safeLogoUrl, safeName, safeUrl, safeUrl, safeUrl, hours);
    }

    private String buildEmailChangeConfirmationTextBody(String userName, String confirmUrl) {
        return """
                Hola %s,

                Solicitaste usar esta dirección como el correo de tu cuenta en Marginalia. Confírmala abriendo este enlace para completar el cambio:

                %s

                Hasta que la confirmes, tu correo actual sigue activo. Este enlace caducará en %d horas. Si no solicitaste este cambio, puedes ignorar este mensaje.
                """.formatted(userName, confirmUrl, verificationProperties.tokenExpirationHours());
    }

    /** Notice to the OLD address that a change was requested, with a link to cancel it. */
    private String buildEmailChangeNoticeHtmlBody(String userName, String newEmail, String cancelUrl) {
        String safeName = HtmlUtils.htmlEscape(userName);
        String safeNewEmail = HtmlUtils.htmlEscape(newEmail);
        String safeUrl = HtmlUtils.htmlEscape(cancelUrl);
        String safeLogoUrl = HtmlUtils.htmlEscape(emailProperties.logoUrl());
        return """
                <div style="display: none; max-height: 0; overflow: hidden; mso-hide: all;">Se solicitó cambiar el correo de tu cuenta de Marginalia.</div>
                <div style="background-color: #faf8f5; padding: 40px 16px; font-family: Georgia, 'Times New Roman', serif;">
                  <div style="max-width: 520px; margin: 0 auto;">
                    <img src="%s" alt="Marginalia" width="120" style="display: block; margin: 0 auto 28px auto; border: 0;">
                    <p style="margin: 0 0 6px 0; text-align: center; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; color: #a8a29e;">&mdash; Seguridad &mdash;</p>
                    <h1 style="margin: 0 0 24px 0; text-align: center; font-size: 26px; font-weight: normal; color: #1c1917;">Se solicit&oacute; un cambio de correo</h1>
                    <p style="margin: 0 0 8px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Hola %s,</p>
                    <p style="margin: 0 0 28px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Se solicit&oacute; cambiar el correo de tu cuenta a <strong>%s</strong>. El cambio no se aplicar&aacute; hasta que se confirme desde esa nueva direcci&oacute;n.</p>
                    <p style="margin: 0 0 8px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Si no fuiste t&uacute;, cancela el cambio ahora:</p>
                    <p style="margin: 0 0 28px 0; text-align: center;">
                      <a href="%s" style="display: inline-block; background-color: #be163d; color: #ffffff; padding: 12px 28px; font-size: 15px; text-decoration: none; border-radius: 4px;">Cancelar el cambio</a>
                    </p>
                    <p style="margin: 0 0 4px 0; font-size: 13px; line-height: 1.6; color: #78716c;">O copia y pega este enlace en tu navegador:</p>
                    <p style="margin: 0 0 28px 0; font-size: 13px; line-height: 1.6; color: #78716c; word-break: break-all;"><a href="%s" style="color: #be163d;">%s</a></p>
                    <div style="border-top: 1px solid #e7e5e4; margin: 0 0 16px 0;"></div>
                    <p style="margin: 0; font-size: 12px; line-height: 1.6; color: #a8a29e;">Si reconoces esta solicitud, no necesitas hacer nada: basta con confirmar desde la nueva direcci&oacute;n.</p>
                  </div>
                </div>
                """.formatted(safeLogoUrl, safeName, safeNewEmail, safeUrl, safeUrl, safeUrl);
    }

    private String buildEmailChangeNoticeTextBody(String userName, String newEmail, String cancelUrl) {
        return """
                Hola %s,

                Se solicitó cambiar el correo de tu cuenta de Marginalia a %s. El cambio no se aplicará hasta que se confirme desde esa nueva dirección.

                Si no fuiste tú, cancela el cambio abriendo este enlace:

                %s

                Si reconoces esta solicitud, no necesitas hacer nada: basta con confirmar desde la nueva dirección.
                """.formatted(userName, newEmail, cancelUrl);
    }

    /** Informational close-the-loop notice to the OLD address once the change is done — no link. */
    private String buildEmailChangeCompletedHtmlBody(String userName, String newEmail) {
        String safeName = HtmlUtils.htmlEscape(userName);
        String safeNewEmail = HtmlUtils.htmlEscape(newEmail);
        String safeLogoUrl = HtmlUtils.htmlEscape(emailProperties.logoUrl());
        return """
                <div style="display: none; max-height: 0; overflow: hidden; mso-hide: all;">El correo de tu cuenta de Marginalia fue cambiado.</div>
                <div style="background-color: #faf8f5; padding: 40px 16px; font-family: Georgia, 'Times New Roman', serif;">
                  <div style="max-width: 520px; margin: 0 auto;">
                    <img src="%s" alt="Marginalia" width="120" style="display: block; margin: 0 auto 28px auto; border: 0;">
                    <p style="margin: 0 0 6px 0; text-align: center; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; color: #a8a29e;">&mdash; Seguridad &mdash;</p>
                    <h1 style="margin: 0 0 24px 0; text-align: center; font-size: 26px; font-weight: normal; color: #1c1917;">Tu correo fue cambiado</h1>
                    <p style="margin: 0 0 8px 0; font-size: 15px; line-height: 1.6; color: #57534e;">Hola %s,</p>
                    <p style="margin: 0 0 28px 0; font-size: 15px; line-height: 1.6; color: #57534e;">El correo de tu cuenta de Marginalia se cambi&oacute; a <strong>%s</strong>. A partir de ahora inicia sesi&oacute;n con esa direcci&oacute;n.</p>
                    <div style="border-top: 1px solid #e7e5e4; margin: 0 0 16px 0;"></div>
                    <p style="margin: 0; font-size: 12px; line-height: 1.6; color: #a8a29e;">Si no fuiste t&uacute;, contacta con soporte de inmediato: tu cuenta puede estar en riesgo.</p>
                  </div>
                </div>
                """.formatted(safeLogoUrl, safeName, safeNewEmail);
    }

    private String buildEmailChangeCompletedTextBody(String userName, String newEmail) {
        return """
                Hola %s,

                El correo de tu cuenta de Marginalia se cambió a %s. A partir de ahora inicia sesión con esa dirección.

                Si no fuiste tú, contacta con soporte de inmediato: tu cuenta puede estar en riesgo.
                """.formatted(userName, newEmail);
    }
}
