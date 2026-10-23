package com.famelsvg.reservasalas.reservation;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ReservationResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReservationRequest req) {
        return ReservationResponse.from(
                service.create(UUID.fromString(jwt.getSubject()), req.roomId(), req.startsAt(), req.endsAt()));
    }

    @GetMapping("/mine")
    List<ReservationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.listMine(UUID.fromString(jwt.getSubject())).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<ReservationResponse> all() {
        return service.listAll().stream().map(ReservationResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    ReservationResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ReservationResponse.from(service.cancel(id, UUID.fromString(jwt.getSubject()), admin));
    }
}
