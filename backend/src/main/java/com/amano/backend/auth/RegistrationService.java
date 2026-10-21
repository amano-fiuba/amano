package com.amano.backend.auth;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.amano.backend.common.EmailAlreadyRegisteredException;
import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.AvatarValidator;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserAvatar;
import com.amano.backend.user.UserAvatarRepository;
import com.amano.backend.user.UserRepository;
import com.amano.backend.verification.EmailResendService;
import com.amano.backend.verification.VerificationMailService;
import com.amano.backend.verification.VerificationTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Atomic account creation (tasks T018 + T033, feature 003 in T020): nothing is
 * persisted when anything fails (FR-014). Normalization before validation is
 * mandated by the clarifications: names trimmed (Q4), email trim+lowercase
 * (FR-004), password policy (FR-006 + Q2), CBU separators stripped before the
 * 22-digit check (Q3), consent required (FR-012). A valid registration creates
 * the account "no verificada" with a single-use token and triggers the
 * verification email; it never starts a session (FR-001, replaces FR-016 of
 * 001). A duplicate email only conflicts when the existing account is
 * VERIFIED (FR-016); over a PENDING account the data is replaced (FR-015). The
 * unique index uq_users_email remains the race-proof backstop (409 via the
 * DataIntegrityViolation handler, SC-004). US3: the optional avatar joins the
 * same validation pass and is persisted (magic-byte true content type, D6)
 * only when the whole request is valid.
 */
@Service
public class RegistrationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@([^\\s@,]+\\.)+[^\\s@,]{2,}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AvatarValidator avatarValidator;
    private final UserAvatarRepository userAvatarRepository;
    private final VerificationTokenService verificationTokenService;
    private final VerificationMailService verificationMailService;
    private final EmailResendService emailResendService;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AvatarValidator avatarValidator, UserAvatarRepository userAvatarRepository,
            VerificationTokenService verificationTokenService,
            VerificationMailService verificationMailService,
            EmailResendService emailResendService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.avatarValidator = avatarValidator;
        this.userAvatarRepository = userAvatarRepository;
        this.verificationTokenService = verificationTokenService;
        this.verificationMailService = verificationMailService;
        this.emailResendService = emailResendService;
    }

    @Transactional
    public User register(RegisterRequest request, MultipartFile avatar) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        String firstName = Optional.ofNullable(request.firstName()).map(String::trim).orElse(null);
        if (firstName == null || firstName.length() < 2 || firstName.length() > 50) {
            fieldErrors.put("firstName", "El nombre debe tener entre 2 y 50 caracteres");
        }

        String lastName = Optional.ofNullable(request.lastName()).map(String::trim).orElse(null);
        if (lastName != null && lastName.isEmpty()) {
            lastName = null; // sent empty → treated as not provided (contract: "puede omitir")
        }
        if (lastName != null && (lastName.length() < 2 || lastName.length() > 50)) {
            fieldErrors.put("lastName", "El apellido debe tener entre 2 y 50 caracteres");
        }

        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            fieldErrors.put("email", "El correo electrónico es obligatorio");
        }
        else if (!EMAIL_PATTERN.matcher(email).matches()) {
            fieldErrors.put("email", "Ingresa un correo electrónico válido");
        }

        String password = request.password();
        if (password == null || password.isEmpty()) {
            fieldErrors.put("password", "La contraseña es obligatoria");
        }
        else {
            String passwordMessage = validatePassword(password);
            if (passwordMessage != null) {
                fieldErrors.put("password", passwordMessage);
            }
        }

        if (request.confirmPassword() != null && !request.confirmPassword().isEmpty()
                && !request.confirmPassword().equals(password)) {
            fieldErrors.put("confirmPassword", "Las contraseñas no coinciden");
        }

        if (!Boolean.TRUE.equals(request.acceptTerms())) {
            fieldErrors.put("acceptTerms", "Debes aceptar los Términos y Condiciones para registrarte");
        }

        Set<Currency> currencies = parseCurrencies(request.currencies(), fieldErrors);
        String cbu = normalizeCbu(request.cbuCvu(), fieldErrors);

        // US3 (T033): the optional avatar joins the same atomic 400 pass.
        String avatarError = avatarValidator.validationError(avatar);
        if (avatarError != null) {
            fieldErrors.put("avatar", avatarError);
        }

        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }

        Optional<User> existingAccount = userRepository.findByEmail(email);
        User user = existingAccount
                .map(existing -> {
                    if (existing.getEmailVerifiedAt() != null) {
                        // FR-016: verified accounts keep 409 "ya se encuentra registrado".
                        throw new EmailAlreadyRegisteredException();
                    }
                    // FR-015: same account, data and password replaced by the new form.
                    return existing;
                })
                .orElseGet(User::new);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCbu(cbu);
        user.setTermsAcceptedAt(OffsetDateTime.now());
        // Q1/FR-011: only the subset chosen by the user is persisted; an empty
        // selection persists nothing (ARS is just the read-time default).
        user.getCurrencies().clear();
        user.getCurrencies().addAll(currencies);

        User saved = userRepository.save(user);
        persistAvatar(avatar, saved);
        if (existingAccount.isPresent()) {
            // FR-015/Q2 (US4): re-registration over a PENDING account always
            // re-issues the token (FR-009), but the mail respects any active
            // resend term: with the term in force, the token is replaced and the
            // mail waits (Q2).
            emailResendService.reissueForPendingAccount(saved);
        }
        else {
            // FR-001/FR-003/D2: the account is created "no verificada" and gets a
            // single-use token; only its SHA-256 hash is persisted (T018).
            String clearToken = verificationTokenService.issueFor(saved);
            // FR-003/D1: fired @Async so the request never waits on SMTP; FR-019: a
            // send failure never fails the registration. FR-011/Q3: the FIRST
            // registration always sends immediately, untouched by any prior term.
            verificationMailService.sendVerificationEmail(saved, clearToken);
        }
        return saved;
    }

    /**
     * US3 (T033): one row in user_avatars with the TRUE content type (derived
     * from magic bytes, decision D6). The association is loaded lazily (D5):
     * no binaries participate in the usual profile reads, only here at creation.
     */
    private void persistAvatar(MultipartFile avatar, User user) {
        if (avatar == null || avatar.isEmpty()) {
            return;
        }
        String contentType = avatarValidator.realContentType(avatar);
        if (contentType == null) {
            return; // defensive: validationError already rejected this case
        }
        UserAvatar stored = new UserAvatar();
        stored.setId(user.getId()); // PK = user_id (data-model: one-to-one shared key)
        // NOTE: the read-only `user` association is NOT set here — the row is
        // written by its @Id column; Hibernate resolves the association on load.
        stored.setContentType(contentType);
        try {
            stored.setData(avatar.getBytes());
        }
        catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        userAvatarRepository.save(stored);
    }

    /** FR-006 + Q2: 8-64 chars, at least one uppercase/lowercase/digit/special. */
    private static String validatePassword(String password) {
        if (password.length() < 8) {
            return "La contraseña debe tener al menos 8 caracteres";
        }
        if (password.length() > 64) {
            return "La contraseña no puede superar los 64 caracteres";
        }
        boolean missingRequirements =
                !password.matches(".*[A-Z].*")
                        || !password.matches(".*[a-z].*")
                        || !password.matches(".*[0-9].*")
                        || !password.matches(".*[^A-Za-z0-9].*");
        if (missingRequirements) {
            return "La contraseña debe incluir una mayúscula, una minúscula, un número y un símbolo";
        }
        return null;
    }

    private static Set<Currency> parseCurrencies(java.util.List<String> requested,
            Map<String, String> fieldErrors) {
        if (requested == null) {
            return Set.of();
        }
        try {
            return requested.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> Currency.valueOf(value.trim()))
                    .collect(java.util.stream.Collectors.toSet());
        }
        catch (IllegalArgumentException invalidCurrency) {
            fieldErrors.put("currencies", "Las monedas deben ser ARS, USD o EUR");
            return Set.of();
        }
    }

    private static String normalizeCbu(String raw, Map<String, String> fieldErrors) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.replace(" ", "").replace("-", "");
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.matches("[0-9]{22}")) {
            fieldErrors.put("cbuCvu", "El CBU/CVU debe tener 22 dígitos");
            return null;
        }
        return normalized;
    }
}
