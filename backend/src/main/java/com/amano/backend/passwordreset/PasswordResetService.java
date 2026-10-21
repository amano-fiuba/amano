package com.amano.backend.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.common.PasswordResetTokenExpiredException;
import com.amano.backend.common.PasswordResetTokenInvalidException;
import com.amano.backend.user.PasswordPolicy;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Password recovery (feature 006).
 *
 * <p>US1, request ({@link #requestReset}, FR-002..FR-007): every call answers the
 * very same neutral message, so the endpoint never reveals whether an address has
 * an account (FR-002). Internal effects, never reflected in the response:
 * <ul>
 *   <li>VERIFIED account → the current tokens are voided, a fresh one is issued
 *       (only its SHA-256 hash is stored) and the link is mailed;</li>
 *   <li>unknown or PENDING account → nothing happens (FR-004).</li>
 * </ul>
 *
 * <p>US2, redemption ({@link #resetPassword}, FR-011..FR-015): the token is
 * checked first (a dead link never asks to fix the password), then the fields;
 * only when both pass is the BCrypt hash replaced and every reset token of the
 * account deleted, in the same transaction.
 */
@Service
public class PasswordResetService {

    /** FR-002: the very same neutral answer in every case, byte by byte. */
    public static final String NEUTRAL_MESSAGE =
            "Si el correo existe, recibirás un enlace para restablecer tu contraseña";

    /** FR-015: answer of a successful reset. */
    public static final String RESET_SUCCESS_MESSAGE =
            "Tu contraseña fue actualizada. Ya puedes iniciar sesión";

    /** 256 bits of randomness, Base64URL without padding (FR-005). */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetMailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;

    public PasswordResetService(UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository, PasswordResetMailService mailService,
            PasswordEncoder passwordEncoder, PasswordResetProperties properties) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /** Handles one "forgot password" request; always returns {@link #NEUTRAL_MESSAGE}. */
    @Transactional
    public String requestReset(String rawEmail) {
        userRepository.findByEmail(normalize(rawEmail))
                .filter(user -> user.getEmailVerifiedAt() != null)
                .ifPresent(user -> mailService.sendPasswordResetEmail(user, issueFor(user)));
        return NEUTRAL_MESSAGE;
    }

    /**
     * Redeems a reset link and replaces the password.
     *
     * <p>{@code noRollbackFor} on the expired branch is deliberate: the tardy
     * redemption deletes the dead token (FR-013) even though the response is the
     * 410 raised right afterwards.
     *
     * @throws PasswordResetTokenInvalidException unknown, redeemed or superseded token (400)
     * @throws PasswordResetTokenExpiredException past the 1-hour window (410)
     * @throws FieldValidationException           policy or confirmation not met (400); token kept
     */
    @Transactional(noRollbackFor = PasswordResetTokenExpiredException.class)
    public String resetPassword(String clearToken, String password, String confirmPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(clearToken == null ? "" : clearToken))
                .filter(found -> found.getUsedAt() == null)
                .orElseThrow(PasswordResetTokenInvalidException::new);

        if (!token.getExpiresAt().isAfter(OffsetDateTime.now())) {
            tokenRepository.delete(token);
            throw new PasswordResetTokenExpiredException();
        }

        validateNewPassword(password, confirmPassword);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        tokenRepository.deleteByUserId(user.getId());
        return RESET_SUCCESS_MESSAGE;
    }

    /** FR-014: policy of 001 plus the confirmation, with the per-field messages of registration. */
    private static void validateNewPassword(String password, String confirmPassword) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (password == null || password.isEmpty()) {
            fieldErrors.put("password", "La contraseña es obligatoria");
        }
        else {
            String policyMessage = PasswordPolicy.validationError(password);
            if (policyMessage != null) {
                fieldErrors.put("password", policyMessage);
            }
        }
        if (confirmPassword == null || !confirmPassword.equals(password)) {
            fieldErrors.put("confirmPassword", "Las contraseñas no coinciden");
        }
        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }
    }

    /**
     * Issues a fresh single-use token and invalidates the current ones (FR-007),
     * returning the clear value to be embedded in the email (never persisted).
     */
    private String issueFor(User user) {
        OffsetDateTime now = OffsetDateTime.now();

        List<PasswordResetToken> current = tokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
        current.forEach(token -> token.setUsedAt(now));
        tokenRepository.saveAll(current);

        byte[] entropy = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(entropy);
        String clearToken = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(clearToken));
        token.setExpiresAt(now.plus(properties.tokenExpiration()));
        tokenRepository.save(token);

        return clearToken;
    }

    /** SHA-256 hex of the clear token: the only form that reaches the database (FR-005). */
    static String hash(String clearToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(clearToken.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available in every JVM", impossible);
        }
    }

    /** Address normalization of 001: trim + lowercase (FR-003). */
    private static String normalize(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
