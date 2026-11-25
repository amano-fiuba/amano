package com.amano.backend.notification;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends notification emails asynchronously (feature 035, plan D15). Like the
 * existing verification and password-reset mail services, a delivery failure is
 * logged and never propagated: the business operation that produced the notice
 * has already succeeded.
 */
@Service
public class NotificationMailService {

    private static final Logger log = LoggerFactory.getLogger(NotificationMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public NotificationMailService(JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Async
    public void send(String to, NotificationType type, String title, String message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(to);
            helper.setSubject("A Mano: " + title);
            helper.setText(message + "\n\nTipo de aviso: " + type.name(), false);
            mailSender.send(mime);
            log.info("Notification email ({}) sent to {}", type, to);
        }
        catch (MessagingException | RuntimeException exception) {
            log.warn("Notification email ({}) to {} could not be sent: {}", type, to, exception.getMessage());
        }
    }
}
