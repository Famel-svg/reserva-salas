package com.famelsvg.reservasalas.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.famelsvg.reservasalas.auth.AuthService;
import com.famelsvg.reservasalas.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

/** Postgres real (um container por JVM) + app completa. Cada teste cria suas proprias salas e usuarios. */
@SpringBootTest(properties = "app.jwt.secret=integration-test-secret-with-at-least-32-bytes")
@AutoConfigureMockMvc
abstract class AbstractPostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected AuthService authService;

    /** Cria o usuario (se nao existir) e devolve o accessToken do login real. */
    protected String login(String email, String password, Role role) throws Exception {
        authService.createUserIfAbsent(email, password, role);
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected String newMember() throws Exception {
        return login("member-" + UUID.randomUUID() + "@test.local", "member-pass-123", Role.MEMBER);
    }

    protected String newAdmin() throws Exception {
        return login("admin-" + UUID.randomUUID() + "@test.local", "admin-pass-123", Role.ADMIN);
    }

    protected UUID createRoom(String adminToken) throws Exception {
        String body = mvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sala " + UUID.randomUUID() + "\",\"capacity\":10}"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected ResultActions reserve(String token, UUID roomId, Instant start, Instant end) throws Exception {
        return mvc.perform(post("/api/reservations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\",\"startsAt\":\"" + start + "\",\"endsAt\":\"" + end + "\"}"));
    }

    protected ResultActions cancel(String token, String reservationId) throws Exception {
        return mvc.perform(delete("/api/reservations/" + reservationId).header("Authorization", "Bearer " + token));
    }

    /** Instante alinhado (hora cheia, UTC) daqui a {@code days} dias; sempre no futuro. */
    protected static Instant at(int days, int hour) {
        return LocalDate.now(ZoneOffset.UTC).plusDays(days).atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }
}
