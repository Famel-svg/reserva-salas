package com.famelsvg.reservasalas.reservation;

import java.util.UUID;

public record SeriesCancellationResponse(UUID seriesId, int cancelled) {}
