package com.amano.backend.verification;

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
 * Unit tests of VerificationMailService (task T017, TDD: written BEFORE the
 * implementation). FR-019/D1/D9: an SMTP failure must NEVER propagate to the
 * registration flow — the @Async method catches it and logs a warning — and the
 * delivered message must be the Spanish mail with the verification link
 * (research.md D8).
 */
class VerificationMailServiceTest {

    private static final String BASE_URL = "http://localhost:5173";

    private JavaMailSender mailSender;
    private VerificationMailService service;
    private User user;

    @BeforeEach
    void setUp() {
        mailSender = Mockito.mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage((jakarta.mail.Session) null));
        // No fromAddress in the unit: the SMTP account only matters for real Gmail (D8).
        service = new VerificationMailService(mailSender, BASE_URL, "");
        user = new User();
        user.setEmail("ana@example.com");
        user.setFirstName("Ana");
    }

    @Test
    void sendsSpanishMailWithTheVerificationLink() throws Exception {
        service.sendVerificationEmail(user, "token-claro-123");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("Verifica tu correo electrónico - A Mano");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("ana@example.com");
        assertThat(decodedBody(message))
                .contains("Hola Ana")
                .contains(BASE_URL + "/verify-email?token=token-claro-123");
    }

    /**
     * JavaMail transfers non-ASCII text as quoted-printable; decode it the way a
     * mail client would so the assertions read the real Spanish body.
     */
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

    @Test
    void smtpFailureIsSwallowedSoRegistrationNeverDependsOnMail() {
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));

        // FR-019: the account is already created; the failure is logged, not propagated.
        assertThatCode(() -> service.sendVerificationEmail(user, "token-claro-123"))
                .doesNotThrowAnyException();
    }
}
