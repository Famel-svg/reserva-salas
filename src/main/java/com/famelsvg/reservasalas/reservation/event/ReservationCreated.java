package com.famelsvg.reservasalas.reservation.event;

import java.time.Instant;
import java.util.UUID;

/** Publicado dentro da transação da criação. Consumidores devem reagir só após o commit. */
public record ReservationCreated(UUID reservationId, UUID roomId, UUID userId, Instant startsAt, Instant endsAt) {
}
