package com.famelsvg.reservasalas.auth;

import com.famelsvg.reservasalas.user.Role;
import com.famelsvg.reservasalas.user.User;
import com.famelsvg.reservasalas.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Único ponto que usa PasswordEncoder: autentica e emite o JWT. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration ttl;
    private final RefreshTokenRepository refreshTokens;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder, Clock clock,
                       @Value("${app.jwt.ttl:PT15M}") Duration ttl,
                       RefreshTokenRepository refreshTokens,
                       @Value("${app.refresh.ttl:P7D}") Duration refreshTtl) {
        this.refreshTokens = refreshTokens;
        this.refreshTtl = refreshTtl;
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        // Mesma exceção para e-mail inexistente e senha errada, para não revelar quais e-mails existem.
        User user = users.findByEmail(normalize(email))
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return issue(user);
    }

    /**
     * Rotação: o token apresentado é revogado e um novo par é emitido. Apresentar um token já revogado
     * indica roubo ou replay, então todos os tokens ativos do usuário são revogados. O noRollbackFor é
     * essencial: sem ele a revogação em massa seria desfeita junto com a exceção do 401.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokens.findByTokenHash(hash(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        // revoke() é condicional: quem perde a corrida de duas requisições simultâneas cai no ramo de reuso.
        if (stored.getRevokedAt() != null || refreshTokens.revoke(stored.getId(), now) == 0) {
            refreshTokens.revokeAllActive(stored.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!stored.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(stored.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        return issue(user);
    }

    /** Silencioso para token desconhecido, para não revelar quais tokens existem. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(hash(refreshToken))
                .ifPresent(t -> refreshTokens.revoke(t.getId(), clock.instant()));
    }

    private TokenResponse issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        String access = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        refreshTokens.save(new RefreshToken(user.getId(), hash(refresh), now.plus(refreshTtl)));
        return new TokenResponse(access, "Bearer", ttl.toSeconds(), refresh);
    }

    /** SHA-256 basta: o token tem 256 bits de entropia, não há o que forçar por dicionário. */
    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void createUserIfAbsent(String email, String rawPassword, Role role) {
        String normalized = normalize(email);
        if (users.findByEmail(normalized).isEmpty()) {
            users.save(new User(normalized, encoder.encode(rawPassword), role));
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
