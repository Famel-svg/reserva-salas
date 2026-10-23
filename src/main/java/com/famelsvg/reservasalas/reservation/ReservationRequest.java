package com.famelsvg.reservasalas.reservation;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ReservationRequest(@NotNull UUID roomId, @NotNull Instant startsAt, @NotNull Instant endsAt) {}
