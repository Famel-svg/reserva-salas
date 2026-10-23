package com.famelsvg.reservasalas.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
