package com.amano.backend.auth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.common.InvalidCredentialsException;
import com.amano.backend.common.UnverifiedAccountException;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Real login (feature 004, replaces the provisional SimulatedLoginController of
 * feature 003). Resolves the typed credentials to a VERIFIED account:
 * - missing email/password → 400 with per-field messages;
 * - unknown account or wrong password → the SAME 401, so the response never
 *   discloses whether the email is registered. An unknown email still runs one
 *   BCrypt comparison against a dummy hash, so both paths take the same time;
 * - valid credentials on a PENDING account → 403 with RESEND_VERIFICATION
 *   (FR-014 of 003). The password is checked first, so the 403 is only revealed
 *   to someone who already knows it.
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Hash compared when the email does not exist (timing equalization). */
    private final String dummyHash;

    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("amano-login-timing-equalizer");
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        String email = request == null || request.email() == null
                ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        String password = request == null ? null : request.password();

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (email.isEmpty()) {
            fieldErrors.put("email", "El correo electrónico es obligatorio");
        }
        if (password == null || password.isEmpty()) {
            fieldErrors.put("password", "La contraseña es obligatoria");
        }
        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash); // same cost as a real check
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (user.getEmailVerifiedAt() == null) {
            throw new UnverifiedAccountException();
        }
        return user;
    }
}
