package com.amano.backend.user;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.common.WrongCurrentPasswordException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Profile mutations for feature 005 (task T005; contracts/users-api.md). Every
 * method loads the account by the SESSION principal id (FR-017): no request
 * ever carries a user id of destination, so third-party data is unreachable.
 * Validations reuse the rules of 001 verbatim (names trim + 2–50 with Q4,
 * CBU separator stripping + 22 digits with Q3, currencies subset with Q1,
 * password policy with FR-006) — the frontend is never trusted (FR-022).
 * Failures validate everything in one pass and throw before any write, so no
 * partial change can ever be applied (FR-019). The password is never logged,
 * returned or passed beyond the single BCrypt call (FR-007 of 001).
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AvatarValidator avatarValidator;
    private final UserAvatarRepository userAvatarRepository;

    public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AvatarValidator avatarValidator, UserAvatarRepository userAvatarRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.avatarValidator = avatarValidator;
        this.userAvatarRepository = userAvatarRepository;
    }

    /** PUT /api/users/me (US1): names, optional CBU and currencies, never the email. */
    @Transactional
    public UserAccountResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null; // session principal vanished: the controller answers 401
        }
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        String firstName = request.firstName() == null ? null : request.firstName().trim();
        if (firstName == null || firstName.length() < 2 || firstName.length() > 50) {
            fieldErrors.put("firstName", "El nombre debe tener entre 2 y 50 caracteres");
        }

        String lastName = nullableTrim(request.lastName());
        if (lastName != null && (lastName.length() < 2 || lastName.length() > 50)) {
            fieldErrors.put("lastName", "El apellido debe tener entre 2 y 50 caracteres");
        }

        Set<Currency> currencies = parseCurrencies(request.currencies(), fieldErrors);
        String cbu = normalizeCbu(request.cbuCvu(), fieldErrors);

        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setCbu(cbu);
        // Q1: only the chosen subset is persisted; empty = no rows (read-time ARS).
        user.getCurrencies().clear();
        user.getCurrencies().addAll(currencies);

        return avatarResponse(user);
    }

    /** PUT /api/users/me/password (US2): the current hash authorizes the credit change. */
    @Transactional
    public UserAccountResponse changePassword(Long userId, UpdatePasswordRequest request) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null; // session principal vanished: the controller answers 401
        }
        String current = request.currentPassword();
        // Wrong/absent accreditation shares the same neutral message: no oracle.
        if (current == null || current.isEmpty()
                || !passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new WrongCurrentPasswordException();
        }

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        String passwordError = PasswordPolicy.validationError(request.newPassword());
        if (passwordError != null) {
            fieldErrors.put("newPassword", passwordError);
        }
        if (request.confirmPassword() == null
                || !request.confirmPassword().equals(request.newPassword())) {
            fieldErrors.put("confirmPassword", "Las contraseñas no coinciden");
        }
        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        return avatarResponse(user);
    }

    /** PUT /api/users/me/avatar (US3): the new binary replaces (or creates) the row. */
    @Transactional
    public UserAccountResponse updateAvatar(Long userId, MultipartFile avatar) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null; // session principal vanished: the controller answers 401
        }
        String missing = "Debes seleccionar un archivo de imagen";
        if (AvatarValidator.isEmpty(avatar)) {
            throw new FieldValidationException(Map.of("avatar", missing));
        }
        avatarValidator.validate(avatar); // 400 format / 413 size (FR-010 of 001)
        UserAvatar stored = userAvatarRepository.findById(userId).orElseGet(UserAvatar::new);
        stored.setId(userId);
        // D6: the stored content type is the TRUE one derived from magic bytes.
        stored.setContentType(avatarValidator.realContentType(avatar));
        try {
            stored.setData(avatar.getBytes());
        }
        catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        userAvatarRepository.save(stored);
        return UserAccountResponse.from(user, true, UserAccountResponse.versionOf(stored.getData()));
    }

    /** DELETE /api/users/me/avatar (US3): back to the default representation. */
    @Transactional
    public UserAccountResponse deleteAvatar(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null; // session principal vanished: the controller answers 401
        }
        // Map BEFORE the bulk delete: with clearAutomatically the context is
        // cleared and the lazy currencies of the detached user cannot load.
        UserAccountResponse response = UserAccountResponse.from(user, false, null);
        userAvatarRepository.deleteRowForUser(userId); // bulk delete: idempotent
        return response;
    }

    /**
     * Own-avatar metadata of a response (hasCustomAvatar + content-derived
     * ?v= so consumers never share a URL across different binaries).
     */
    private UserAccountResponse avatarResponse(User user) {
        UserAvatar avatar = userAvatarRepository.findById(user.getId()).orElse(null);
        return UserAccountResponse.from(user, avatar != null,
                avatar == null ? null : UserAccountResponse.versionOf(avatar.getData()));
    }

    private static String nullableTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Q1: subset of {@link Currency} (027: the BCRA catalog), no duplicates; empty list is also valid. */
    private static Set<Currency> parseCurrencies(java.util.List<String> requested,
            Map<String, String> fieldErrors) {
        if (requested == null) {
            return Set.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        try {
            for (String raw : requested) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Currency currency = Currency.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                if (!seen.add(currency.name())) {
                    fieldErrors.put("currencies", "Las monedas no deben repetirse");
                    return Set.of();
                }
            }
        }
        catch (IllegalArgumentException invalidCurrency) {
            fieldErrors.put("currencies", "Moneda no soportada");
            return Set.of();
        }
        return seen.stream().map(Currency::valueOf).collect(java.util.stream.Collectors.toSet());
    }

    /** Q3: separator spaces/dashes stripped BEFORE the 22-digit check. */
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
