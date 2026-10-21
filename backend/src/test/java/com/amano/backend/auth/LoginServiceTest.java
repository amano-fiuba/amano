package com.amano.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.common.InvalidCredentialsException;
import com.amano.backend.common.UnverifiedAccountException;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/** Unit tests of LoginService (feature 004): every branch in isolation. */
class LoginServiceTest {

    /** Low cost factor: fast tests, same algorithm as production. */
    private final PasswordEncoder encoder = Mockito.spy(new BCryptPasswordEncoder(4));

    private UserRepository userRepository;
    private LoginService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        service = new LoginService(userRepository, encoder);
    }

    private User account(String email, String password, boolean verified) {
        User user = new User();
        user.setId(42L);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setEmailVerifiedAt(verified ? OffsetDateTime.now() : null);
        return user;
    }

    @Test
    void verifiedAccountWithRightPasswordIsAuthenticated() {
        User ana = account("ana@example.com", "Password123!", true);
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

        assertThat(service.authenticate(new LoginRequest("ana@example.com", "Password123!")))
                .isSameAs(ana);
    }

    @Test
    void emailIsTrimmedAndLowercasedBeforeTheLookup() {
        User ana = account("ana@example.com", "Password123!", true);
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

        assertThat(service.authenticate(new LoginRequest("  ANA@Example.com ", "Password123!")))
                .isSameAs(ana);
        verify(userRepository).findByEmail("ana@example.com");
    }

    @Test
    void wrongPasswordIsInvalidCredentials() {
        User ana = account("ana@example.com", "Password123!", true);
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("ana@example.com", "otra")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(InvalidCredentialsException.MESSAGE);
    }

    @Test
    void unknownEmailIsTheSameInvalidCredentialsAndStillRunsAHashComparison() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        Mockito.clearInvocations(encoder);

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("nadie@example.com", "Password123!")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(InvalidCredentialsException.MESSAGE);
        verify(encoder).matches(eq("Password123!"), anyString());
    }

    @Test
    void pendingAccountWithRightPasswordIsUnverified() {
        User ana = account("ana@example.com", "Password123!", false);
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("ana@example.com", "Password123!")))
                .isInstanceOf(UnverifiedAccountException.class);
    }

    @Test
    void pendingAccountWithWrongPasswordDoesNotRevealItsState() {
        User ana = account("ana@example.com", "Password123!", false);
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("ana@example.com", "otra")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void missingFieldsAreReportedTogetherWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.authenticate(new LoginRequest("   ", "")))
                .isInstanceOfSatisfying(FieldValidationException.class, exception ->
                        assertThat(exception.getFields())
                                .containsEntry("email", "El correo electrónico es obligatorio")
                                .containsEntry("password", "La contraseña es obligatoria"));
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void nullBodyIsTreatedAsMissingFields() {
        assertThatThrownBy(() -> service.authenticate(null))
                .isInstanceOfSatisfying(FieldValidationException.class, exception ->
                        assertThat(exception.getFields()).containsKeys("email", "password"));
    }
}
