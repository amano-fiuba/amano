package com.amano.backend.passwordreset;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.amano.backend.user.User;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends the password reset email (feature 006, FR-008). Same pattern as
 * {@code VerificationMailService}: {@code @Async} so the request never waits on
 * SMTP (and its timing does not reveal whether a mail was sent), and any failure
 * is logged without propagating, so the endpoint keeps its neutral 200.
 *
 * <p>The link is {@code ${app.base-url}/reset-password?token=<clear token>}; the
 * clear token only exists in this message (FR-005).
 */
@Service
public class PasswordResetMailService {

    static final String SUBJECT = "Restablece tu contraseña - A Mano";

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final String fromAddress;

    public PasswordResetMailService(JavaMailSender mailSender,
            @Value("${app.base-url}") String baseUrl,
            @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
        this.fromAddress = fromAddress;
    }

    /** Sends the Spanish reset mail. Failures are logged with a warning and never propagated. */
    @Async
    public void sendPasswordResetEmail(User user, String clearToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(user.getEmail());
            helper.setSubject(SUBJECT);
            helper.setText(body(user, clearToken), false);
            mailSender.send(message);
            log.info("Password reset email sent to {}", user.getEmail());
        }
        catch (MessagingException | RuntimeException exception) {
            // Never log the token or the SMTP credentials (constitution VIII).
            log.warn("Password reset email to {} could not be sent: {}",
                    user.getEmail(), exception.getMessage());
        }
    }

    private String body(User user, String clearToken) {
        String link = baseUrl + "/reset-password?token=" + clearToken;
        String name = user.getFirstName() == null || user.getFirstName().isBlank()
                ? "" : " " + user.getFirstName();
        return """
                Hola%s,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta en A Mano. Para elegir una nueva, haz clic en el siguiente enlace (válido durante 1 hora):

                %s

                Si tú no solicitaste este cambio, puedes ignorar este mensaje: tu contraseña actual seguirá funcionando.
                """.formatted(name, link);
    }
}
