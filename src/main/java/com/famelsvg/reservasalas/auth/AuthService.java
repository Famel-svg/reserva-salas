package com.famelsvg.reservasalas.auth;

import com.famelsvg.reservasalas.user.Role;
import com.famelsvg.reservasalas.user.User;
import com.famelsvg.reservasalas.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Único ponto que usa PasswordEncoder: autentica e emite o JWT. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration ttl;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder, Clock clock,
                       @Value("${app.jwt.ttl:PT15M}") Duration ttl) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    public TokenResponse login(String email, String password) {
        // Mesma exceção para e-mail inexistente e senha errada, para não revelar quais e-mails existem.
        User user = users.findByEmail(normalize(email))
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds());
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
