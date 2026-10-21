package com.amano.backend.verification;

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
 * Sends the verification email (task T019, decisions D1/D8 of research.md).
 * The method runs {@code @Async} so registration never waits on SMTP, and any
 * failure is caught and logged without exposing internal details or failing the
 * already-created account (FR-019).
 *
 * <p>The link is {@code ${app.base-url}/verify-email?token=<clear token>}; the
 * clear token only exists in this message (D2). Sending real mail is a manual
 * dev/demo action: the automated tests point {@code spring.mail.*} at GreenMail
 * and never contact Gmail (D9, team rule).
 */
@Service
public class VerificationMailService {

    static final String SUBJECT = "Verifica tu correo electrónico - A Mano";

    private static final Logger log = LoggerFactory.getLogger(VerificationMailService.class);

    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final String fromAddress;

    public VerificationMailService(JavaMailSender mailSender,
            @Value("${app.base-url}") String baseUrl,
            @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
        this.fromAddress = fromAddress;
    }

    /**
     * Sends the Spanish verification mail. Failures are logged with a warning and
     * never propagated: the account is already created and the user can resend
     * (FR-019).
     */
    @Async
    public void sendVerificationEmail(User user, String clearToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            // With a real SMTP account (Gmail, D8) the From must be the authenticated
            // mailbox; tests run without credentials, so the header is omitted.
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(user.getEmail());
            helper.setSubject(SUBJECT);
            helper.setText(body(user, clearToken), false);
            mailSender.send(message);
            log.info("Verification email sent to {} (expires in 24 h)", user.getEmail());
        }
        // MailException extends RuntimeException: catch both roots (checked + unchecked).
        catch (MessagingException | RuntimeException exception) {
            // Never log the token or the SMTP credentials (constitution VIII).
            log.warn("Verification email to {} could not be sent: {}",
                    user.getEmail(), exception.getMessage());
        }
    }

    private String body(User user, String clearToken) {
        String link = baseUrl + "/verify-email?token=" + clearToken;
        String name = user.getFirstName() == null || user.getFirstName().isBlank()
                ? "" : " " + user.getFirstName();
        return """
                Hola%s,

                Gracias por registrarte en A Mano. Para verificar tu correo electrónico y activar tu cuenta, haz clic en el siguiente enlace (válido durante 24 horas):

                %s

                Si tú no solicitaste este registro, puedes ignorar este mensaje.
                """.formatted(name, link);
    }
}
