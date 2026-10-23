package com.famelsvg.reservasalas.room;

import java.util.UUID;

public record RoomResponse(UUID id, String name, int capacity) {
    static RoomResponse from(Room r) {
        return new RoomResponse(r.getId(), r.getName(), r.getCapacity());
    }
}
