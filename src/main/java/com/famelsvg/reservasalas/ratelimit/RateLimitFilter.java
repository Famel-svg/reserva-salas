package com.famelsvg.reservasalas.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit por IP de origem (ADR 0006). Roda antes da cadeia do Spring Security, logo não conhece o
 * usuário: a chave é {@code getRemoteAddr()}. A resposta 429 é escrita aqui mesmo, pois exceções do filtro
 * não passam pelo GlobalExceptionHandler.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/auth/login";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE");
    /** Acima disso, a próxima requisição varre e remove os buckets ociosos. */
    static final int MAX_KEYS = 10_000;
    private static final long IDLE_NANOS = Duration.ofMinutes(2).toNanos();

    private static final class Entry {
        final Bucket bucket;
        volatile long lastSeen;

        Entry(Bucket bucket) {
            this.bucket = bucket;
        }
    }

    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();
    private final int loginPerMinute;
    private final int writePerMinute;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.ratelimit.login-per-minute:10}") int loginPerMinute,
            @Value("${app.ratelimit.write-per-minute:60}") int writePerMinute) {
        this.objectMapper = objectMapper;
        this.loginPerMinute = loginPerMinute;
        this.writePerMinute = writePerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean login = "POST".equals(req.getMethod()) && LOGIN_PATH.equals(path);
        boolean write = !login && path.startsWith("/api/") && WRITE_METHODS.contains(req.getMethod());
        if (!login && !write) {
            chain.doFilter(req, res);
            return;
        }
        ConsumptionProbe probe = consume((login ? "login:" : "write:") + req.getRemoteAddr(),
                login ? loginPerMinute : writePerMinute);
        if (probe.isConsumed()) {
            chain.doFilter(req, res);
            return;
        }
        long retryAfter = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).plusMillis(999).toSeconds());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Limite de requisições excedido. Tente novamente em " + retryAfter + " segundos.");
        problem.setTitle("Muitas requisições");
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(retryAfter));
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(), problem);
    }

    private ConsumptionProbe consume(String key, int perMinute) {
        long now = System.nanoTime();
        if (buckets.size() > MAX_KEYS) {
            buckets.values().removeIf(e -> now - e.lastSeen > IDLE_NANOS);
        }
        Entry entry = buckets.computeIfAbsent(key, k -> new Entry(Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build()));
        entry.lastSeen = now;
        return entry.bucket.tryConsumeAndReturnRemaining(1);
    }
}
