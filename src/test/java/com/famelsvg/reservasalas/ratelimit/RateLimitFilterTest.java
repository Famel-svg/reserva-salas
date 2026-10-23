package com.famelsvg.reservasalas.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class RateLimitFilterTest {

    @RestController
    static class Dummy {
        @RequestMapping("/api/**")
        String any() {
            return "ok";
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Dummy())
                .addFilters(new RateLimitFilter(new ObjectMapper(), 3, 5))
                .build();
    }

    private static RequestPostProcessor ip(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }

    @Test
    void loginIsLimitedWith429RetryAfterAndProblemDetail() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/login"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.title").value("Muitas requisições"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void writesAreLimitedAcrossPostPutDelete() throws Exception {
        mvc.perform(post("/api/rooms")).andExpect(status().isOk());
        mvc.perform(put("/api/rooms/1")).andExpect(status().isOk());
        mvc.perform(delete("/api/rooms/1")).andExpect(status().isOk());
        mvc.perform(post("/api/rooms")).andExpect(status().isOk());
        mvc.perform(post("/api/rooms")).andExpect(status().isOk());
        mvc.perform(delete("/api/rooms/1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/login")).andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/login").with(ip("10.0.0.2"))).andExpect(status().isOk());
    }

    @Test
    void readsAreNotLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            mvc.perform(get("/api/rooms")).andExpect(status().isOk());
        }
    }
}
