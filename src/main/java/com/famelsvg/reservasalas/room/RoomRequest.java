package com.famelsvg.reservasalas.room;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RoomRequest(@NotBlank String name, @Min(1) int capacity) {}
