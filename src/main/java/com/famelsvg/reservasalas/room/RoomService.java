package com.famelsvg.reservasalas.room;

import com.famelsvg.reservasalas.common.error.InvalidRequestException;
import com.famelsvg.reservasalas.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private final RoomRepository rooms;

    public RoomService(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @Transactional(readOnly = true)
    public List<Room> list() {
        return rooms.findAll();
    }

    @Transactional(readOnly = true)
    public Room get(UUID id) {
        return rooms.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sala não encontrada"));
    }

    @Transactional
    public Room create(String name, int capacity) {
        checkCapacity(capacity);
        return rooms.save(new Room(name, capacity));
    }

    @Transactional
    public Room update(UUID id, String name, int capacity) {
        checkCapacity(capacity);
        Room room = get(id);
        room.update(name, capacity);
        return room;
    }

    @Transactional
    public void delete(UUID id) {
        rooms.delete(get(id));
    }

    private static void checkCapacity(int capacity) {
        if (capacity < 1) {
            throw new InvalidRequestException("A capacidade deve ser de pelo menos 1");
        }
    }
}
