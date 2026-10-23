package com.famelsvg.reservasalas.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.famelsvg.reservasalas.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RefreshFlowIT extends AbstractPostgresIT {

    private String loginBody() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@test.local";
        authService.createUserIfAbsent(email, "refresh-pass-123", Role.MEMBER);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"refresh-pass-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions postToken(String path, String refreshToken) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private static String field(String json, String name) {
        return JsonPath.read(json, "$." + name);
    }

    @Test
    void refreshRotatesAndNewAccessTokenWorks() throws Exception {
        String r1 = field(loginBody(), "refreshToken");

        String body = postToken("/api/auth/refresh", r1).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(field(body, "refreshToken")).isNotEqualTo(r1);
        mvc.perform(get("/api/reservations").header("Authorization", "Bearer " + field(body, "accessToken")))
                .andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        String r1 = field(loginBody(), "refreshToken");
        postToken("/api/auth/refresh", r1).andExpect(status().isOk());

        postToken("/api/auth/refresh", r1).andExpect(status().isUnauthorized());
    }

    @Test
    void reuseRevokesEveryActiveTokenOfTheUser() throws Exception {
        String r1 = field(loginBody(), "refreshToken");
        String r2 = field(postToken("/api/auth/refresh", r1).andReturn().getResponse().getContentAsString(),
                "refreshToken");

        postToken("/api/auth/refresh", r1).andExpect(status().isUnauthorized()); // reuso do token já rotacionado
        postToken("/api/auth/refresh", r2).andExpect(status().isUnauthorized()); // o ativo também caiu
    }

    @Test
    void unknownTokenIs401ProblemDetail() throws Exception {
        postToken("/api/auth/refresh", "nao-existe").andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void logoutRevokesTokenAndUnknownTokenIs204() throws Exception {
        String r1 = field(loginBody(), "refreshToken");

        postToken("/api/auth/logout", r1).andExpect(status().isNoContent());
        postToken("/api/auth/refresh", r1).andExpect(status().isUnauthorized());
        postToken("/api/auth/logout", "nao-existe").andExpect(status().isNoContent());
    }
}
