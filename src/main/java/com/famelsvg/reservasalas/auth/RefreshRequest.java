package com.famelsvg.reservasalas.auth;

import jakarta.validation.constraints.NotBlank;

/** Corpo de /refresh e /logout. */
public record RefreshRequest(@NotBlank(message = "refreshToken é obrigatório") String refreshToken) {
}
