package com.amano.backend.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.amano.backend.user.User;
import com.icegreen.greenmail.util.GreenMailUtil;

import jakarta.mail.internet.MimeMessage;

/**
 * Unit tests of PasswordResetMailService (feature 006, TDD: written BEFORE the
 * implementation). FR-008: an SMTP failure never propagates, and the delivered
 * message is the Spanish mail with the {@code /reset-password} link.
 */
class PasswordResetMailServiceTest {

    private static final String BASE_URL = "http://localhost:5173";

    private JavaMailSender mailSender;
    private PasswordResetMailService service;
    private User user;

    @BeforeEach
    void setUp() {
        mailSender = Mockito.mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage((jakarta.mail.Session) null));
        service = new PasswordResetMailService(mailSender, BASE_URL, "");
        user = new User();
        user.setEmail("ana@example.com");
        user.setFirstName("Ana");
    }

    @Test
    void sendsSpanishMailWithTheResetLink() throws Exception {
        service.sendPasswordResetEmail(user, "token-claro-123");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("Restablece tu contraseña - A Mano");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("ana@example.com");
        assertThat(decodedBody(message))
                .contains("Hola Ana")
                .contains(BASE_URL + "/reset-password?token=token-claro-123")
                .contains("1 hora");
    }

    @Test
    void smtpFailureIsSwallowed() {
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));

        // FR-008: the endpoint answers the neutral 200 whatever happens with SMTP.
        assertThatCode(() -> service.sendPasswordResetEmail(user, "token-claro-123"))
                .doesNotThrowAnyException();
    }

    /** Decodes quoted-printable the way a mail client would (see VerificationMailServiceTest). */
    private static String decodedBody(MimeMessage message) {
        String raw = GreenMailUtil.getBody(message).replace("=\r\n", "").replace("=\n", "");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '=' && i + 2 < raw.length()) {
                try {
                    bytes.write(Integer.parseInt(raw.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                }
                catch (NumberFormatException notAnEscape) {
                    // literal '=' that was not encoded: fall through and copy it
                }
            }
            bytes.write((byte) c);
        }
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
