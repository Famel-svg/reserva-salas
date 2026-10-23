package com.famelsvg.reservasalas.it;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Clock de teste: relógio do sistema até alguém chamar {@link #freezeAt}; {@link #reset} volta ao normal.
 * Congelar só vale para o relógio da aplicação: a validação do JWT (exp) usa o relógio real, então faça
 * os logins antes de congelar.
 */
public class AdjustableClock extends Clock {

    private final AtomicReference<Instant> frozen = new AtomicReference<>();

    public void freezeAt(Instant instant) {
        frozen.set(instant);
    }

    public void reset() {
        frozen.set(null);
    }

    @Override
    public Instant instant() {
        Instant f = frozen.get();
        return f != null ? f : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
