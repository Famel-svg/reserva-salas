package com.famelsvg.reservasalas.room;

import com.famelsvg.reservasalas.common.error.InvalidRequestException;
import com.famelsvg.reservasalas.common.error.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de sala. Leituras são cacheadas em "rooms" como {@link RoomResponse} (nunca a entidade JPA, que não
 * serializa bem e carregaria estado de persistência). Qualquer escrita descarta o cache inteiro, porque a
 * lista também depende de cada sala. Ver ADR 0008.
 */
@Service
public class RoomService {

    public static final String CACHE = "rooms";

    private final RoomRepository rooms;

    public RoomService(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @Cacheable(CACHE)
    @Transactional(readOnly = true)
    public List<RoomResponse> list() {
        // ArrayList, não toList(): o Jackson não regrava a lista imutável do JDK com tipo embutido.
        return rooms.findAll().stream().map(RoomResponse::from).collect(Collectors.toCollection(ArrayList::new));
    }

    @Cacheable(value = CACHE, key = "#id")
    @Transactional(readOnly = true)
    public RoomResponse get(UUID id) {
        return RoomResponse.from(find(id));
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public RoomResponse create(String name, int capacity) {
        checkCapacity(capacity);
        return RoomResponse.from(rooms.save(new Room(name, capacity)));
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public RoomResponse update(UUID id, String name, int capacity) {
        checkCapacity(capacity);
        Room room = find(id);
        room.update(name, capacity);
        return RoomResponse.from(room);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void delete(UUID id) {
        rooms.delete(find(id));
    }

    private Room find(UUID id) {
        return rooms.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sala não encontrada"));
    }

    private static void checkCapacity(int capacity) {
        if (capacity < 1) {
            throw new InvalidRequestException("A capacidade deve ser de pelo menos 1");
        }
    }
}
