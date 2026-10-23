package com.famelsvg.reservasalas.reservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    boolean existsByRoomIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID roomId, ReservationStatus status, Instant end, Instant start);

    long countByUserIdAndStatusAndEndsAtAfter(UUID userId, ReservationStatus status, Instant now);

    List<Reservation> findByUserIdOrderByStartsAtDesc(UUID userId);
}
