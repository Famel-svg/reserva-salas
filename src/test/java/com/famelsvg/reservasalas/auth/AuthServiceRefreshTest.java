package com.famelsvg.reservasalas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.famelsvg.reservasalas.user.Role;
import com.famelsvg.reservasalas.user.User;
import com.famelsvg.reservasalas.user.UserRepository;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceRefreshTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final User user = new User("a@test.local", "hash", Role.MEMBER);
    private final UUID userId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", userId);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(
                Jwt.withTokenValue("jwt").header("alg", "HS256").subject("x").build());
    }

    private AuthService service(Instant now) {
        return new AuthService(users, encoder, jwtEncoder, Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(15), tokens, Duration.ofDays(7));
    }

    /** Registra no mock um token armazenado para o valor em texto informado. */
    private RefreshToken stored(String raw, Instant expiresAt, Instant revokedAt) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes()));
        RefreshToken t = new RefreshToken(userId, hash, expiresAt);
        ReflectionTestUtils.setField(t, "id", tokenId);
        ReflectionTestUtils.setField(t, "revokedAt", revokedAt);
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void rotationRevokesOldAndIssuesNewPairStoringOnlyHash() throws Exception {
        stored("old", NOW.plusSeconds(60), null);
        when(tokens.revoke(tokenId, NOW)).thenReturn(1);

        TokenResponse res = service(NOW).refresh("old");

        assertThat(res.accessToken()).isEqualTo("jwt");
        assertThat(res.refreshToken()).isNotBlank().isNotEqualTo("old").hasSize(43); // 32 bytes em Base64 URL
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(res.refreshToken()).hasSize(64);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        verify(tokens, never()).revokeAllActive(any(), any());
    }

    @Test
    void reusingRevokedTokenRevokesAllActiveAndFails() throws Exception {
        stored("old", NOW.plusSeconds(60), NOW.minusSeconds(5));

        assertThatThrownBy(() -> service(NOW).refresh("old")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(tokens).revokeAllActive(userId, NOW);
        verify(tokens, never()).save(any());
    }

    @Test
    void losingTheRevocationRaceIsTreatedAsReuse() throws Exception {
        stored("old", NOW.plusSeconds(60), null);
        when(tokens.revoke(tokenId, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service(NOW).refresh("old")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(tokens).revokeAllActive(userId, NOW);
    }

    @Test
    void expiredTokenFailsWithoutIssuing() throws Exception {
        stored("old", NOW.minusSeconds(1), null);
        when(tokens.revoke(tokenId, NOW)).thenReturn(1);

        assertThatThrownBy(() -> service(NOW).refresh("old")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(tokens, never()).save(any());
        verify(tokens, never()).revokeAllActive(any(), any());
    }

    @Test
    void unknownTokenFails() {
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(NOW).refresh("nope")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesKnownTokenAndIgnoresUnknown() throws Exception {
        stored("known", NOW.plusSeconds(60), null);

        service(NOW).logout("known");
        service(NOW).logout("unknown");

        verify(tokens).revoke(tokenId, NOW);
    }
}
