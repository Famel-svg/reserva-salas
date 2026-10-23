package com.famelsvg.reservasalas.reservation.event;

import java.time.Instant;
import java.util.UUID;

/** Publicado dentro da transação do cancelamento. Consumidores devem reagir só após o commit. */
public record ReservationCancelled(UUID reservationId, UUID roomId, UUID userId, Instant startsAt, Instant endsAt) {
}
