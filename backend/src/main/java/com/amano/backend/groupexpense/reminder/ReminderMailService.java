package com.amano.backend.groupexpense.reminder;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends the reminder mails (025 contracts §5/§6). Deliberately synchronous and
 * without catching the mail exceptions: the dispatcher needs the failure to
 * mark the row {@code FAILED} and notify the admins. Tests point
 * {@code spring.mail.*} at GreenMail, never at a real SMTP.
 */
@Service
public class ReminderMailService {

    private static final Logger log = LoggerFactory.getLogger(ReminderMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public ReminderMailService(JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    /**
     * Sends one plain-text mail. Any {@link MessagingException} or runtime
     * failure of the SMTP transport is propagated to the caller.
     */
    public void send(String to, ReminderMessageBuilder.Mail mail) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        if (fromAddress != null && !fromAddress.isBlank()) {
            helper.setFrom(fromAddress);
        }
        helper.setTo(to);
        helper.setSubject(mail.subject());
        helper.setText(mail.body(), false);
        mailSender.send(message);
        log.info("Reminder mail '{}' sent to {}", mail.subject(), to);
    }
}
