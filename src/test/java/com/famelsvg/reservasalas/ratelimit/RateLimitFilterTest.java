package com.famelsvg.reservasalas.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
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
    private final AtomicLong nanos = new AtomicLong();
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new ObjectMapper(), 3, 5);
        filter.nanoTime = nanos::get;
        mvc = MockMvcBuilders.standaloneSetup(new Dummy())
                .addFilters(filter)
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

    private void loginFromDistinctIps(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mvc.perform(post("/api/auth/login").with(ip("ip-" + i))).andExpect(status().isOk());
        }
    }

    @Test
    void idleBucketsAreSweptOnlyAfterKeyLimitIsExceeded() throws Exception {
        loginFromDistinctIps(RateLimitFilter.MAX_KEYS);
        nanos.addAndGet(Duration.ofMinutes(3).toNanos());

        // exatamente no corte (size == MAX_KEYS, nao maior): nao varre
        mvc.perform(post("/api/auth/login").with(ip("new-1"))).andExpect(status().isOk());
        assertThat(filter.trackedKeys()).isEqualTo(RateLimitFilter.MAX_KEYS + 1);

        // acima do corte: remove os 10.000 ociosos e preserva o recem-usado
        mvc.perform(post("/api/auth/login").with(ip("new-2"))).andExpect(status().isOk());
        assertThat(filter.trackedKeys()).isEqualTo(2);
    }

    @Test
    void bucketsIdleForExactlyTwoMinutesSurviveTheSweep() throws Exception {
        loginFromDistinctIps(RateLimitFilter.MAX_KEYS + 1);

        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        mvc.perform(post("/api/auth/login").with(ip("new-1"))).andExpect(status().isOk());
        assertThat(filter.trackedKeys()).isEqualTo(RateLimitFilter.MAX_KEYS + 2);

        nanos.addAndGet(1);
        mvc.perform(post("/api/auth/login").with(ip("new-2"))).andExpect(status().isOk());
        assertThat(filter.trackedKeys()).isEqualTo(2); // new-1 tem 1ns de ociosidade; os demais passaram de 2 min
    }
}
