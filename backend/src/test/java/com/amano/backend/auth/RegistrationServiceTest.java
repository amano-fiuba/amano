package com.amano.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.amano.backend.common.EmailAlreadyRegisteredException;
import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;
import com.amano.backend.verification.EmailResendLimit;
import com.amano.backend.verification.EmailResendLimitRepository;
import com.amano.backend.verification.EmailResendService;
import com.amano.backend.verification.VerificationMailService;
import com.amano.backend.verification.VerificationProperties;
import com.amano.backend.verification.VerificationToken;
import com.amano.backend.verification.VerificationTokenRepository;
import com.amano.backend.verification.VerificationTokenService;

/**
 * Unit tests of the normalization/validation rules of RegistrationService
 * (FR-002..FR-006, FR-009, FR-012 and clarifications Q2/Q3/Q4) plus the
 * feature-003 behaviors (task T011, TDD: written BEFORE the implementation):
 * FR-001 (account left unverified), FR-003/FR-009 (hashed token issued, mail
 * triggered, previous token invalidated), FR-015 (re-registration over a
 * pending account replaces the data), FR-016 (409 only for a verified
 * duplicate). The happy path plus the 409 race are also covered by
 * RegistrationIT (Testcontainers); this suite covers the branches in isolation.
 */
class RegistrationServiceTest {

    private UserRepository userRepository;
    private VerificationTokenRepository tokenRepository;
    private VerificationMailService mailService;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, User.class));
        tokenRepository = Mockito.mock(VerificationTokenRepository.class);
        when(tokenRepository.save(any(VerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, VerificationToken.class));
        mailService = Mockito.mock(VerificationMailService.class);
        // Real token lifecycle over the mocked repository (T018 is exercised through its collaborator).
        VerificationProperties properties = new VerificationProperties(Duration.ofHours(24),
                Duration.ofMinutes(5), Duration.ofDays(3), "0 0 * * * *");
        VerificationTokenService tokenService = new VerificationTokenService(tokenRepository, properties);
        // Real resend service over a mocked limit repository (US4: the term logic is
        // exercised through RegistrationService's collaborator, T054).
        EmailResendLimitRepository limitRepository = Mockito.mock(EmailResendLimitRepository.class);
        when(limitRepository.findById(anyString())).thenReturn(Optional.empty());
        when(limitRepository.save(any(EmailResendLimit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, EmailResendLimit.class));
        EmailResendService emailResendService = new EmailResendService(limitRepository, userRepository,
                tokenService, mailService, properties);
        service = new RegistrationService(userRepository, new BCryptPasswordEncoder(),
                new com.amano.backend.user.AvatarValidator(),
                Mockito.mock(com.amano.backend.user.UserAvatarRepository.class),
                tokenService, mailService, emailResendService);
    }

    /** Independent SHA-256 implementation (D2): the test never reuses production hashing. */
    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static RegisterRequest request(
            String firstName, String lastName, String email, String password,
            String confirmPassword, Boolean acceptTerms, List<String> currencies, String cbu) {
        return new RegisterRequest(firstName, lastName, email, password, confirmPassword,
                acceptTerms, currencies, cbu);
    }

    /** Runs register() expecting a FieldValidationException; returns its field map. */
    private Map<String, String> errorFieldsOf(RegisterRequest failingRequest) {
        try {
            service.register(failingRequest, emptyAvatar());
            throw new AssertionError("request must not be valid: " + failingRequest);
        }
        catch (FieldValidationException exception) {
            return exception.getFields();
        }
    }

    private static org.springframework.web.multipart.MultipartFile emptyAvatar() {
        return new MockMultipartFile("avatar", new byte[0]);
    }

    @Test
    void savesNormalizedUserWithHashedPassword() {
        User saved = service.register(request("  Ana  ", " Pérez ", "  Ana.Perez@Example.COM ",
                "Password123!", "Password123!", true, List.of("ARS", "USD"),
                "285-05910-0123456789 0123"), emptyAvatar());

        assertThat(saved.getFirstName()).isEqualTo("Ana");                 // Q4 trim
        assertThat(saved.getLastName()).isEqualTo("Pérez");
        assertThat(saved.getEmail()).isEqualTo("ana.perez@example.com");   // FR-004
        assertThat(saved.getPasswordHash()).startsWith("$2");              // FR-007 BCrypt
        assertThat(saved.getCbu()).isEqualTo("2850591001234567890123");    // Q3 normalized
        assertThat(saved.getTermsAcceptedAt()).isNotNull();                // FR-012
        assertThat(saved.getCurrencies()).containsExactlyInAnyOrder(
                Currency.ARS, Currency.USD);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void rejectsFirstNameTooShortAfterTrim() {
        assertThat(errorFieldsOf(request(" A ", "Pérez", "ana@example.com",
                "Password123!", "Password123!", true, null, null)).get("firstName"))
                .isEqualTo("El nombre debe tener entre 2 y 50 caracteres");
    }

    @Test
    void rejectsInvalidLastNameWhenProvided() {
        assertThat(errorFieldsOf(request("Ana", "A", "ana@example.com",
                "Password123!", "Password123!", true, null, null)).get("lastName"))
                .isEqualTo("El apellido debe tener entre 2 y 50 caracteres");
    }

    @Test
    void treatsBlankLastNameAsAbsent() {
        User saved = service.register(request("Ana", "   ", "ana@example.com",
                "Password123!", "Password123!", true, null, null), emptyAvatar());
        assertThat(saved.getLastName()).isNull();
    }

    @Test
    void rejectsInvalidAndBlankEmail() {
        assertThat(errorFieldsOf(request("Ana", null, "sin-arroba",
                "Password123!", "Password123!", true, null, null)).get("email"))
                .isEqualTo("Ingresa un correo electrónico válido");
        assertThat(errorFieldsOf(request("Ana", null, "   ",
                "Password123!", "Password123!", true, null, null)).get("email"))
                .isEqualTo("El correo electrónico es obligatorio");
    }

    @Test
    void rejectsPasswordPolicyBranches() {
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "Pa1!", "Pa1!", true, null, null)).get("password"))
                .isEqualTo("La contraseña debe tener al menos 8 caracteres");
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "a".repeat(62) + "B1!", "a".repeat(62) + "B1!", true, null, null)).get("password"))
                .isEqualTo("La contraseña no puede superar los 64 caracteres");
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "password123!", "password123!", true, null, null)).get("password"))
                .isEqualTo("La contraseña debe incluir una mayúscula, una minúscula, un número y un símbolo");
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "Password!!!!", "Password!!!!", true, null, null)).get("password"))
                .isEqualTo("La contraseña debe incluir una mayúscula, una minúscula, un número y un símbolo");
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "PASSWORD1234", "PASSWORD1234", true, null, null)).get("password"))
                .isEqualTo("La contraseña debe incluir una mayúscula, una minúscula, un número y un símbolo");
    }

    @Test
    void rejectsMismatchedConfirmation() {
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "Password123!", "Password124!", true, null, null)).get("confirmPassword"))
                .isEqualTo("Las contraseñas no coinciden");
    }

    @Test
    void missingConsentIsRejected() {
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "Password123!", "Password123!", null, null, null)).get("acceptTerms"))
                .isEqualTo("Debes aceptar los Términos y Condiciones para registrarte");
    }

    @Test
    void falseConsentIsRejected() {
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "Password123!", "Password123!", false, null, null)).get("acceptTerms"))
                .isEqualTo("Debes aceptar los Términos y Condiciones para registrarte");
    }

    @Test
    void rejectsInvalidCurrencyValues() {
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "Password123!", "Password123!", true, List.of("ARS", "BOB"), null)).get("currencies"))
                .isEqualTo("Las monedas deben ser ARS, USD o EUR");
    }

    @Test
    void rejectsCbuWithWrongLengthOrCharacters() {
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "Password123!", "Password123!", true, null, "12345")).get("cbuCvu"))
                .isEqualTo("El CBU/CVU debe tener 22 dígitos");
        assertThat(errorFieldsOf(request("Ana", null, "ana@example.com",
                "Password123!", "Password123!", true, null,
                "2850591001234567890ABC3")).get("cbuCvu"))
                .isEqualTo("El CBU/CVU debe tener 22 dígitos");
    }

    @Test
    void rejectsEverythingInvalidAtOnceAndPersistsNothing() {
        Map<String, String> fields = errorFieldsOf(request("A", "A", "nope",
                "debil", "nope", false, List.of("XXL"), "123"));
        assertThat(fields.keySet()).containsExactlyInAnyOrder(
                "firstName", "lastName", "email", "password", "confirmPassword",
                "acceptTerms", "currencies", "cbuCvu");
        Mockito.verify(userRepository, Mockito.never()).save(any(User.class));
    }

    /** FR-001/FR-003/D2: unverified account, only the SHA-256 hash persisted, mail fired with the clear token. */
    @Test
    void createsUnverifiedAccountIssuesHashedTokenAndTriggersMail() {
        User saved = service.register(request("  Ana  ", null, "Ana.Perez@Example.COM",
                "Password123!", "Password123!", true, null, null), emptyAvatar());

        assertThat(saved.getEmailVerifiedAt()).isNull(); // FR-001: still "no verificada"

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        VerificationToken persisted = tokenCaptor.getValue();
        assertThat(persisted.getUser()).isSameAs(saved);
        assertThat(persisted.getTokenHash()).matches("[0-9a-f]{64}"); // SHA-256 hex, never the clear value
        assertThat(persisted.getExpiresAt())
                .isBetween(OffsetDateTime.now().plusHours(23), OffsetDateTime.now().plusHours(25)); // 24 h

        ArgumentCaptor<String> clearToken = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationEmail(any(User.class), clearToken.capture());
        assertThat(clearToken.getValue()).isNotBlank();
        assertThat(persisted.getTokenHash()).isEqualTo(sha256Hex(clearToken.getValue()));
        assertThat(persisted.getTokenHash()).isNotEqualTo(clearToken.getValue()); // D2: clear token never stored
    }

    /** FR-015 + FR-009: re-registration over a PENDING account replaces data/password and invalidates the previous token. */
    @Test
    void reRegistrationOverPendingReplacesDataInvalidatesTokenAndTriggersMail() {
        User pending = new User();
        pending.setId(42L);
        pending.setFirstName("Tercero");
        pending.setEmail("ana@example.com");
        pending.setPasswordHash("$2a$oldhash");
        VerificationToken previous = new VerificationToken();
        previous.setUser(pending);
        previous.setTokenHash("a".repeat(64));
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(pending));
        when(tokenRepository.findByUserIdAndUsedAtIsNull(42L)).thenReturn(List.of(previous));

        User saved = service.register(request("Ana", "  ", " ANA@example.com ",
                "NuevaClave123!", "NuevaClave123!", true, null, null), emptyAvatar());

        assertThat(saved).isSameAs(pending); // FR-015: same account, no second row
        assertThat(saved.getFirstName()).isEqualTo("Ana");
        assertThat(saved.getLastName()).isNull();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo("$2a$oldhash");
        assertThat(saved.getEmailVerifiedAt()).isNull();
        assertThat(previous.getUsedAt()).isNotNull(); // FR-009: previous token invalidated
        verify(userRepository).save(pending);
        // US1 scope: the resend-limit check lands in US4, so the mail is always triggered here.
        verify(mailService).sendVerificationEmail(any(User.class), anyString());
    }

    /** FR-016: a VERIFIED duplicate keeps the 001 behavior — 409, no account touched, no mail. */
    @Test
    void duplicateOverVerifiedAccountIsRejectedWithoutSendingMail() {
        User verified = new User();
        verified.setId(7L);
        verified.setEmail("ocupada@example.com");
        verified.setEmailVerifiedAt(OffsetDateTime.now());
        when(userRepository.findByEmail("ocupada@example.com")).thenReturn(Optional.of(verified));

        assertThatThrownBy(() -> service.register(request("Ana", null, "ocupada@example.com",
                "Password123!", "Password123!", true, null, null), emptyAvatar()))
                .isInstanceOf(EmailAlreadyRegisteredException.class); // FR-016

        Mockito.verify(userRepository, Mockito.never()).save(any(User.class));
        Mockito.verify(mailService, Mockito.never()).sendVerificationEmail(any(), anyString()); // FR-011: no mail
        Mockito.verify(tokenRepository, Mockito.never()).save(any(VerificationToken.class));
    }
}
