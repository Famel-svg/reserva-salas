package com.famelsvg.reservasalas.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RecurringReservationIT extends AbstractPostgresIT {

    private ResultActions recurring(String token, UUID room, Instant start, Instant end, int occurrences)
            throws Exception {
        return mvc.perform(post("/api/reservations/recurring")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + room + "\",\"startsAt\":\"" + start + "\",\"endsAt\":\"" + end
                        + "\",\"occurrences\":" + occurrences + "}"));
    }

    private String mine(String token) throws Exception {
        return mvc.perform(get("/api/reservations/mine").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions cancelSeries(String token, String seriesId) throws Exception {
        return mvc.perform(delete("/api/reservations/series/" + seriesId).header("Authorization", "Bearer " + token));
    }

    private String seriesOf(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$[0].seriesId");
    }

    @Test
    void threeOccurrencesShareOneSeriesId() throws Exception {
        UUID room = createRoom(newAdmin());
        String user = newMember();

        String body = recurring(user, room, at(30, 10), at(30, 11), 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        List<String> series = JsonPath.read(body, "$[*].seriesId");
        assertThat(series).hasSize(3).doesNotContainNull().containsOnly(series.get(0));
        List<String> starts = JsonPath.read(body, "$[*].startsAt");
        assertThat(starts).containsExactly(at(30, 10).toString(), at(37, 10).toString(), at(44, 10).toString());
    }

    @Test
    void conflictingOccurrenceSavesNothing() throws Exception {
        UUID room = createRoom(newAdmin());
        String other = newMember();
        String user = newMember();
        reserve(other, room, at(51, 10), at(51, 11)).andExpect(status().isCreated());

        recurring(user, room, at(44, 10), at(44, 11), 3)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString(at(51, 10).toString())));

        List<?> reservations = JsonPath.read(mine(user), "$");
        assertThat(reservations).isEmpty();
    }

    @Test
    void occurrencesOutOfRangeReturns400() throws Exception {
        UUID room = createRoom(newAdmin());
        String user = newMember();
        recurring(user, room, at(30, 10), at(30, 11), 1).andExpect(status().isBadRequest());
        recurring(user, room, at(30, 10), at(30, 11), 4).andExpect(status().isBadRequest());
    }

    @Test
    void ownerCancelsSeriesIgnoringOccurrencesInsideDeadline() throws Exception {
        UUID room = createRoom(newAdmin());
        String user = newMember();
        // ~1h from now, aligned to 15 min and in the future: first occurrence is inside the 2h cancel deadline
        long slot = Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond() / 900 * 900;
        Instant start = Instant.ofEpochSecond(slot);
        String series = seriesOf(recurring(user, room, start, start.plus(1, ChronoUnit.HOURS), 3)
                .andExpect(status().isCreated()));

        cancelSeries(user, series).andExpect(status().isOk()).andExpect(jsonPath("$.cancelled").value(2));

        List<String> statuses = JsonPath.read(mine(user), "$[*].status");
        assertThat(statuses).containsExactlyInAnyOrder("ACTIVE", "CANCELLED", "CANCELLED");
    }

    @Test
    void otherUsersSeriesReturns404() throws Exception {
        UUID room = createRoom(newAdmin());
        String owner = newMember();
        String stranger = newMember();
        String series = seriesOf(recurring(owner, room, at(60, 10), at(60, 11), 2).andExpect(status().isCreated()));

        cancelSeries(stranger, series).andExpect(status().isNotFound());
        cancelSeries(owner, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }
}
