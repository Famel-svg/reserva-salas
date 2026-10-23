package com.famelsvg.reservasalas.reservation;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id, UUID roomId, UUID userId, Instant startsAt, Instant endsAt, ReservationStatus status) {
    static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(), r.getRoom().getId(), r.getUserId(), r.getStartsAt(), r.getEndsAt(), r.getStatus());
    }
}
