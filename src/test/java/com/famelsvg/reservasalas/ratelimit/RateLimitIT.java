package com.famelsvg.reservasalas.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.famelsvg.reservasalas.it.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Prova que o filtro está na cadeia real da aplicação (Security + MVC). Usa um IP dedicado para não
 * consumir o bucket dos outros ITs e o limite padrão de 10 logins/min (a base sobe o limite para 1000;
 * isto cria um contexto próprio, aceito pelo custo de um só arranque).
 */
@TestPropertySource(properties = "app.ratelimit.login-per-minute=10")
class RateLimitIT extends AbstractPostgresIT {

    private static final String IP = "203.0.113.77";

    private ResultActions badLogin() throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(IP);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ninguem@test.local\",\"password\":\"errada-123\"}"));
    }

    @Test
    void eleventhLoginFromSameIpIs429() throws Exception {
        for (int i = 0; i < 10; i++) {
            badLogin().andExpect(status().isUnauthorized());
        }

        badLogin().andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }
}
