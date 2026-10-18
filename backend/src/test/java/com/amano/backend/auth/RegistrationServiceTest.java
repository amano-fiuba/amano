package com.amano.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Unit tests of the normalization/validation rules of RegistrationService
 * (FR-002..FR-006, FR-009, FR-012 and clarifications Q2/Q3/Q4). The happy path
 * plus the 409 race are also covered by RegistrationIT (Testcontainers); this
 * suite covers the remaining rule branches in isolation.
 */
class RegistrationServiceTest {

    private UserRepository userRepository;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, User.class));
        service = new RegistrationService(userRepository, new BCryptPasswordEncoder(),
                new com.amano.backend.user.AvatarValidator(),
                Mockito.mock(com.amano.backend.user.UserAvatarRepository.class));
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
}
