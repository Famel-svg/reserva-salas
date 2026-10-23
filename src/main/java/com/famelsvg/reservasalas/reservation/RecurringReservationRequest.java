package com.famelsvg.reservasalas.reservation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record RecurringReservationRequest(
        @NotNull UUID roomId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Min(2) @Max(3) int occurrences) {}
