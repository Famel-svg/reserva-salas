package com.famelsvg.reservasalas.reservation;

import com.famelsvg.reservasalas.common.error.BusinessRuleException;
import com.famelsvg.reservasalas.common.error.ConflictException;
import com.famelsvg.reservasalas.common.error.InvalidRequestException;
import com.famelsvg.reservasalas.common.error.ResourceNotFoundException;
import com.famelsvg.reservasalas.room.Room;
import com.famelsvg.reservasalas.room.RoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final Clock clock;
    private final Duration maxDuration;
    private final Duration cancelDeadline;
    private final int maxActivePerUser;
    private final Duration slot;

    public ReservationService(
            ReservationRepository reservations,
            RoomRepository rooms,
            Clock clock,
            @Value("${app.reservation.max-duration}") Duration maxDuration,
            @Value("${app.reservation.cancel-deadline}") Duration cancelDeadline,
            @Value("${app.reservation.max-active-per-user}") int maxActivePerUser,
            @Value("${app.reservation.slot}") Duration slot) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.clock = clock;
        this.maxDuration = maxDuration;
        this.cancelDeadline = cancelDeadline;
        this.maxActivePerUser = maxActivePerUser;
        this.slot = slot;
    }

    @Transactional
    public Reservation create(UUID userId, UUID roomId, Instant startsAt, Instant endsAt) {
        if (!startsAt.isBefore(endsAt)) {
            throw new InvalidRequestException("O início deve ser anterior ao fim");
        }
        if (!aligned(startsAt) || !aligned(endsAt)) {
            throw new InvalidRequestException("Início e fim devem estar alinhados a intervalos de 15 minutos");
        }
        if (Duration.between(startsAt, endsAt).compareTo(maxDuration) > 0) {
            throw new InvalidRequestException("A duração máxima da reserva é de 4 horas");
        }
        Instant now = clock.instant();
        if (startsAt.isBefore(now)) {
            throw new BusinessRuleException("Não é possível reservar no passado");
        }
        if (reservations.countByUserIdAndStatusAndEndsAtAfter(userId, ReservationStatus.ACTIVE, now)
                >= maxActivePerUser) {
            throw new BusinessRuleException("Limite de " + maxActivePerUser + " reservas ativas atingido");
        }
        Room room = rooms.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("Sala não encontrada"));
        if (reservations.existsByRoomIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                roomId, ReservationStatus.ACTIVE, endsAt, startsAt)) {
            throw new ConflictException("Sala já reservada neste horário");
        }
        return reservations.saveAndFlush(new Reservation(room, userId, startsAt, endsAt));
    }

    @Transactional(readOnly = true)
    public List<Reservation> listMine(UUID userId) {
        return reservations.findByUserIdOrderByStartsAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Reservation> listAll() {
        return reservations.findAll();
    }

    @Transactional
    public Reservation cancel(UUID reservationId, UUID userId, boolean admin) {
        Reservation r = reservations
                .findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva não encontrada"));
        if (!admin) {
            if (!r.getUserId().equals(userId)) {
                throw new AccessDeniedException("Reserva de outro usuário");
            }
            if (clock.instant().isAfter(r.getStartsAt().minus(cancelDeadline))) {
                throw new BusinessRuleException("Cancelamento só é permitido até 2 horas antes do início");
            }
        }
        r.cancel();
        return r;
    }

    private boolean aligned(Instant t) {
        return t.getNano() == 0 && t.getEpochSecond() % slot.toSeconds() == 0;
    }
}
