package com.famelsvg.reservasalas.it;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.famelsvg.reservasalas.room.RoomRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Prova o cache com um spy no repositório: se a segunda leitura não chama {@code findById}/{@code findAll},
 * ela veio do Redis (que é real, via Testcontainers).
 */
class RoomCacheIT extends AbstractPostgresIT {

    @MockitoSpyBean RoomRepository repository;

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void secondReadByIdDoesNotHitDatabase() throws Exception {
        String member = newMember();
        UUID id = createRoom(newAdmin());
        clearInvocations(repository);

        mvc.perform(get("/api/rooms/" + id).header("Authorization", bearer(member))).andExpect(status().isOk());
        mvc.perform(get("/api/rooms/" + id).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(repository, times(1)).findById(id);
    }

    @Test
    void secondListDoesNotHitDatabase() throws Exception {
        String member = newMember();
        createRoom(newAdmin());
        clearInvocations(repository);

        mvc.perform(get("/api/rooms").header("Authorization", bearer(member))).andExpect(status().isOk());
        mvc.perform(get("/api/rooms").header("Authorization", bearer(member))).andExpect(status().isOk());

        verify(repository, times(1)).findAll();
    }

    @Test
    void updateIsVisibleOnNextRead() throws Exception {
        String admin = newAdmin();
        UUID id = createRoom(admin);
        mvc.perform(get("/api/rooms/" + id).header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/rooms").header("Authorization", bearer(admin))).andExpect(status().isOk());

        mvc.perform(put("/api/rooms/" + id)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nome novo\",\"capacity\":9}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/rooms/" + id).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.name").value("Nome novo"))
                .andExpect(jsonPath("$.capacity").value(9));
        mvc.perform(get("/api/rooms").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].name").value("Nome novo"));
    }

    @Test
    void deletedRoomDisappearsFromListAndGet() throws Exception {
        String admin = newAdmin();
        UUID id = createRoom(admin);
        mvc.perform(get("/api/rooms").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[*].id", hasItem(id.toString())));
        mvc.perform(get("/api/rooms/" + id).header("Authorization", bearer(admin))).andExpect(status().isOk());

        mvc.perform(delete("/api/rooms/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/rooms").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[*].id", not(hasItem(id.toString()))));
        mvc.perform(get("/api/rooms/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }
}
