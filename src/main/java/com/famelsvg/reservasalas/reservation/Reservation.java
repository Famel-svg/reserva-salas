package com.famelsvg.reservasalas.reservation;

import com.famelsvg.reservasalas.room.Room;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Reservation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    private UUID userId;
    /** Compartilhado pelas ocorrências de uma recorrência; null em reservas avulsas. */
    private UUID seriesId;
    private Instant startsAt;
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    protected Reservation() {}

    public Reservation(Room room, UUID userId, Instant startsAt, Instant endsAt) {
        this(room, userId, startsAt, endsAt, null);
    }

    public Reservation(Room room, UUID userId, Instant startsAt, Instant endsAt, UUID seriesId) {
        this.seriesId = seriesId;
        this.id = UUID.randomUUID();
        this.room = room;
        this.userId = userId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = ReservationStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public Room getRoom() { return room; }
    public UUID getUserId() { return userId; }
    public UUID getSeriesId() { return seriesId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public ReservationStatus getStatus() { return status; }

    public void cancel() { this.status = ReservationStatus.CANCELLED; }
}
