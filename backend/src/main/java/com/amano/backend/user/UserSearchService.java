package com.amano.backend.user;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;

/**
 * User search (feature 008): validates the term on the server (FR-004/FR-019,
 * the UI check is not enough — constitution VIII), normalizes it and maps the
 * matches to the minimal DTO. Invalid terms never reach the database.
 */
@Service
public class UserSearchService {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;
    public static final int MAX_RESULTS = 20;

    public static final String TOO_SHORT_MESSAGE = "Ingresá al menos 2 caracteres para buscar";
    public static final String TOO_LONG_MESSAGE = "La búsqueda no puede superar los 100 caracteres";

    private final UserRepository userRepository;

    public UserSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSearchResult> search(Long currentUserId, String rawTerm) {
        String term = rawTerm == null ? "" : rawTerm.strip();
        if (term.length() < MIN_LENGTH) {
            throw new FieldValidationException(Map.of("q", TOO_SHORT_MESSAGE));
        }
        if (term.length() > MAX_LENGTH) {
            throw new FieldValidationException(Map.of("q", TOO_LONG_MESSAGE));
        }
        String normalized = term.toLowerCase(Locale.ROOT);
        return userRepository
                .searchVerified(normalized, currentUserId, PageRequest.of(0, MAX_RESULTS))
                .stream()
                .map(UserSearchResult::from)
                .toList();
    }
}
