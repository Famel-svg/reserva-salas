package com.famelsvg.reservasalas.it;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Redis fora do ar, ponta a ponta (ADR 0008): o cache aponta para uma porta sem ninguém ouvindo
 * (connection refused real, sem timeout) e as leituras devem seguir 200 vindas do banco.
 * Escolhido em vez de pausar o container Redis compartilhado da JVM: não afeta os outros ITs,
 * não há risco de deixá-lo parado e o teste não espera timeouts do Lettuce.
 */
@Import(RedisOutageIT.DeadRedis.class)
class RedisOutageIT extends AbstractPostgresIT {

    @TestConfiguration
    static class DeadRedis {
        @Bean
        @Primary
        RedisConnectionFactory deadRedisConnectionFactory() {
            return new LettuceConnectionFactory("127.0.0.1", 1);
        }
    }

    @Test
    void roomReadsComeFromDatabaseWhenRedisIsDown() throws Exception {
        String admin = newAdmin();
        UUID id = createRoom(admin); // put/evict do cache falham e sao ignorados

        mvc.perform(get("/api/rooms/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mvc.perform(get("/api/rooms").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id.toString())));
    }
}
