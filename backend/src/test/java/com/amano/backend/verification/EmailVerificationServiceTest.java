package com.amano.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.common.VerificationTokenExpiredException;
import com.amano.backend.common.VerificationTokenInvalidException;
import com.amano.backend.common.VerificationTokenUsedException;
import com.amano.backend.user.User;

/**
 * Unit tests of the redemption rules (task T027, TDD: written BEFORE the
 * implementation). FR-005..FR-009: a valid token marks itself used and verifies
 * the account; a used one is a 409; an expired one is a 410 AND records its use;
 * an unknown/tampered one is a 400. Repositories are mocked (no database).
 */
class EmailVerificationServiceTest {

    private VerificationTokenRepository repository;
    private VerificationTokenService tokenService;
    private EmailVerificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(VerificationTokenRepository.class);
        tokenService = Mockito.mock(VerificationTokenService.class);
        service = new EmailVerificationService(repository, tokenService);
        user = new User();
        user.setEmail("ana@example.com");
        when(tokenService.hash(any(String.class))).thenReturn("hash");
    }

    private VerificationToken token(OffsetDateTime expiresAt, OffsetDateTime usedAt) {
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setTokenHash("hash");
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        return token;
    }

    /** FR-005/FR-009: the valid token is consumed and the account turns verified. */
    @Test
    void validTokenMarksItUsedAndVerifiesTheAccount() {
        VerificationToken token = token(OffsetDateTime.now().plusHours(24), null);
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        User verified = service.redeem("token-claro");

        assertThat(verified).isSameAs(user);
        assertThat(verified.getEmailVerifiedAt()).isNotNull();
        assertThat(token.getUsedAt()).isNotNull();
        verify(repository).save(token);
    }

    /** FR-007: a used token is a conflict and changes nothing. */
    @Test
    void usedTokenIsRejectedAsConflictAndChangesNothing() {
        VerificationToken token = token(OffsetDateTime.now().plusHours(24), OffsetDateTime.now().minusMinutes(1));
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.redeem("token-claro"))
                .isInstanceOf(VerificationTokenUsedException.class);
        assertThat(user.getEmailVerifiedAt()).isNull();
        verify(repository, never()).save(any(VerificationToken.class));
    }

    /** FR-006/FR-021: the expired token is a 410 and its use is recorded (D2). */
    @Test
    void expiredTokenIsRejectedAsGoneAndItsUseIsRecorded() {
        VerificationToken token = token(OffsetDateTime.now().minusMinutes(1), null);
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.redeem("token-claro"))
                .isInstanceOf(VerificationTokenExpiredException.class);
        assertThat(token.getUsedAt()).isNotNull();
        assertThat(user.getEmailVerifiedAt()).isNull();
        verify(repository).save(token);
    }

    /** FR-008: an unknown or tampered token is a bad request. */
    @Test
    void unknownOrTamperedTokenIsRejectedAsBadRequest() {
        when(repository.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem("token-alterado"))
                .isInstanceOf(VerificationTokenInvalidException.class);
        assertThat(user.getEmailVerifiedAt()).isNull();
    }
}
