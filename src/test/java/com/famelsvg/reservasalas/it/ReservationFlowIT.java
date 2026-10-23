package com.famelsvg.reservasalas.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ReservationFlowIT extends AbstractPostgresIT {

    private String idOf(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void overlappingReservationReturns409() throws Exception {
        UUID room = createRoom(newAdmin());
        String a = newMember();
        String b = newMember();

        reserve(a, room, at(10, 10), at(10, 12)).andExpect(status().isCreated());

        reserve(b, room, at(10, 11), at(10, 13))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        reserve(b, room, at(10, 10), at(10, 12)).andExpect(status().isConflict());
        reserve(b, room, at(10, 9), at(10, 11)).andExpect(status().isConflict());
    }

    @Test
    void touchingReservationsBothSucceed() throws Exception {
        UUID room = createRoom(newAdmin());
        String user = newMember();

        reserve(user, room, at(11, 10), at(11, 11)).andExpect(status().isCreated());
        reserve(user, room, at(11, 11), at(11, 12)).andExpect(status().isCreated());
    }

    @Test
    void cancelledSlotCanBeBookedAgain() throws Exception {
        UUID room = createRoom(newAdmin());
        String a = newMember();
        String b = newMember();

        String id = idOf(reserve(a, room, at(12, 10), at(12, 11)).andExpect(status().isCreated()));
        cancel(a, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        reserve(b, room, at(12, 10), at(12, 11)).andExpect(status().isCreated());
    }

    @Test
    void cancelingSomeoneElsesReservationReturns403() throws Exception {
        UUID room = createRoom(newAdmin());
        String owner = newMember();
        String other = newMember();

        String id = idOf(reserve(owner, room, at(13, 10), at(13, 11)).andExpect(status().isCreated()));

        cancel(other, id).andExpect(status().isForbidden());
        cancel(owner, id).andExpect(status().isOk());
    }

    @Test
    void adminCancelsAnyReservationAndListsAll() throws Exception {
        String admin = newAdmin();
        UUID room = createRoom(admin);
        String owner = newMember();

        String id = idOf(reserve(owner, room, at(14, 10), at(14, 11)).andExpect(status().isCreated()));

        mvc.perform(get("/api/reservations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reservations").header("Authorization", "Bearer " + owner))
                .andExpect(status().isForbidden());
        cancel(admin, id).andExpect(status().isOk());
    }

    @Test
    void fourthActiveReservationReturns422() throws Exception {
        UUID room = createRoom(newAdmin());
        String user = newMember();

        for (int h = 8; h < 11; h++) {
            reserve(user, room, at(15, h), at(15, h + 1)).andExpect(status().isCreated());
        }

        reserve(user, room, at(15, 12), at(15, 13)).andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/reservations/mine").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void invalidIntervalsReturn400() throws Exception {
        UUID room = createRoom(newAdmin());
        String user = newMember();

        reserve(user, room, at(16, 11), at(16, 10)).andExpect(status().isBadRequest());
        reserve(user, room, at(16, 10), at(16, 10)).andExpect(status().isBadRequest());
        reserve(user, room, at(16, 10).plus(5, ChronoUnit.MINUTES), at(16, 11)).andExpect(status().isBadRequest());
        reserve(user, room, at(16, 8), at(16, 13)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/reservations").header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservationInThePastReturns422() throws Exception {
        UUID room = createRoom(newAdmin());
        String user = newMember();

        reserve(user, room, at(-2, 10), at(-2, 11)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownRoomReturns404() throws Exception {
        reserve(newMember(), UUID.randomUUID(), at(17, 10), at(17, 11)).andExpect(status().isNotFound());
    }

    @Test
    void ownerCannotCancelLessThan2HoursBefore() throws Exception {
        UUID room = createRoom(newAdmin());
        String user = newMember();
        // proximo multiplo de 15 min entre 30 e 45 min a frente: no futuro e dentro das 2 h de prazo
        long slot = Duration.ofMinutes(15).getSeconds();
        Instant start = Instant.ofEpochSecond((Instant.now().plus(30, ChronoUnit.MINUTES).getEpochSecond() / slot + 1) * slot);

        String id = idOf(reserve(user, room, start, start.plus(1, ChronoUnit.HOURS)).andExpect(status().isCreated()));

        cancel(user, id).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cancelUnknownReservationReturns404() throws Exception {
        cancel(newMember(), UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        newMember();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.local\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsBearerToken() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@test.local";
        authService.createUserIfAbsent(email, "some-pass-123", com.famelsvg.reservasalas.user.Role.MEMBER);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"some-pass-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void roomCrudRespectsRoles() throws Exception {
        String admin = newAdmin();
        String member = newMember();
        String json = "{\"name\":\"Sala X\",\"capacity\":6}";

        mvc.perform(post("/api/rooms").header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rooms").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"capacity\":0}"))
                .andExpect(status().isBadRequest());

        String body = mvc.perform(post("/api/rooms").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/rooms/" + id).header("Authorization", "Bearer " + member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(6));
        mvc.perform(get("/api/rooms").header("Authorization", "Bearer " + member)).andExpect(status().isOk());
        mvc.perform(put("/api/rooms/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sala Y\",\"capacity\":8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Sala Y"));
        mvc.perform(delete("/api/rooms/" + id).header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/rooms/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/rooms/" + id).header("Authorization", "Bearer " + member))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
    }
}
