package com.famelsvg.reservasalas.room;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    /**
     * SELECT ... FOR UPDATE. Serializa reservas concorrentes da mesma sala antes do INSERT: sem isso, varios
     * INSERTs simultaneos no mesmo intervalo entram em deadlock na EXCLUDE gist (40P01 -> 500, ~1 s por vitima).
     * A EXCLUDE continua como garantia final.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") UUID id);
}
