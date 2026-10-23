package com.famelsvg.reservasalas.room;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService service;

    public RoomController(RoomService service) {
        this.service = service;
    }

    @GetMapping
    List<RoomResponse> list() {
        return service.list().stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/{id}")
    RoomResponse get(@PathVariable UUID id) {
        return RoomResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    RoomResponse create(@Valid @RequestBody RoomRequest req) {
        return RoomResponse.from(service.create(req.name(), req.capacity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    RoomResponse update(@PathVariable UUID id, @Valid @RequestBody RoomRequest req) {
        return RoomResponse.from(service.update(id, req.name(), req.capacity()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
