package com.famelsvg.reservasalas.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.famelsvg.reservasalas.reservation.ReservationController;
import com.famelsvg.reservasalas.reservation.ReservationService;
import com.famelsvg.reservasalas.room.RoomController;
import com.famelsvg.reservasalas.room.RoomResponse;
import com.famelsvg.reservasalas.room.RoomService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {RoomController.class, ReservationController.class},
        properties = "app.jwt.secret=" + SecurityWebMvcTest.SECRET)
@Import({SecurityConfig.class, JwtKeyConfig.class, SecurityProblemHandler.class})
class SecurityWebMvcTest {

    static final String SECRET = "unit-test-secret-with-more-than-32-bytes!!";
    private static final String OTHER_SECRET = "another-secret-also-with-more-than-32-bytes";
    private static final String ROOM_JSON = "{\"name\":\"Sala\",\"capacity\":5}";

    @Autowired MockMvc mvc;
    @MockitoBean RoomService roomService;
    @MockitoBean ReservationService reservationService;

    private static String token(String secret, String role, Instant expiresAt) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .issuedAt(expiresAt.minus(15, ChronoUnit.MINUTES))
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String valid(String role) {
        return token(SECRET, role, Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String expired = token(SECRET, "MEMBER", Instant.now().minus(1, ChronoUnit.HOURS));
        mvc.perform(get("/api/rooms").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyReturns401() throws Exception {
        String forged = token(OTHER_SECRET, "ADMIN", Instant.now().plus(10, ChronoUnit.MINUTES));
        mvc.perform(post("/api/rooms").header("Authorization", "Bearer " + forged)
                        .contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenReturns401() throws Exception {
        mvc.perform(get("/api/rooms").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCannotCreateRoom() throws Exception {
        mvc.perform(post("/api/rooms").header("Authorization", "Bearer " + valid("MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotListAllReservations() throws Exception {
        mvc.perform(get("/api/reservations").header("Authorization", "Bearer " + valid("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCanListRooms() throws Exception {
        when(roomService.list()).thenReturn(List.of(new RoomResponse(java.util.UUID.randomUUID(), "A", 3)));
        mvc.perform(get("/api/rooms").header("Authorization", "Bearer " + valid("MEMBER")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCreateRoom() throws Exception {
        when(roomService.create("Sala", 5)).thenReturn(new RoomResponse(java.util.UUID.randomUUID(), "Sala", 5));
        mvc.perform(post("/api/rooms").header("Authorization", "Bearer " + valid("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isCreated());
    }
}
