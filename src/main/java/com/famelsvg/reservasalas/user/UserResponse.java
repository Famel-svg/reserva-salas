package com.famelsvg.reservasalas.user;

import java.util.UUID;

public record UserResponse(UUID id, String email, Role role) {
}
