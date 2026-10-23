package com.famelsvg.reservasalas.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.famelsvg.reservasalas.common.error.BusinessRuleException;
import com.famelsvg.reservasalas.common.error.ConflictException;
import com.famelsvg.reservasalas.common.error.InvalidRequestException;
import com.famelsvg.reservasalas.common.error.ResourceNotFoundException;
import com.famelsvg.reservasalas.room.Room;
import com.famelsvg.reservasalas.room.RoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T08:00:00Z");

    @Mock ReservationRepository reservations;
    @Mock RoomRepository rooms;

    private ReservationService service;
    private final UUID userId = UUID.randomUUID();
    private final Room room = new Room("Sala A", 10);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ReservationService(
                reservations, rooms, clock, Duration.ofHours(4), Duration.ofHours(2), 3, Duration.ofMinutes(15));
    }

    private static Instant at(String time) {
        return Instant.parse("2030-01-01T" + time + "Z");
    }

    private void roomExistsAndFree() {
        when(rooms.findByIdForUpdate(room.getId())).thenReturn(Optional.of(room));
        when(reservations.saveAndFlush(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Reservation create(Instant start, Instant end) {
        return service.create(userId, room.getId(), start, end);
    }

    // ---- create ----

    @Test
    void createsActiveReservation() {
        roomExistsAndFree();

        Reservation r = create(at("10:00:00"), at("11:00:00"));

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(r.getUserId()).isEqualTo(userId);
        assertThat(r.getRoom()).isSameAs(room);
        assertThat(r.getStartsAt()).isEqualTo(at("10:00:00"));
        assertThat(r.getEndsAt()).isEqualTo(at("11:00:00"));
    }

    @Test
    void rejectsStartEqualToEnd() {
        assertThatThrownBy(() -> create(at("10:00:00"), at("10:00:00"))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsStartAfterEnd() {
        assertThatThrownBy(() -> create(at("11:00:00"), at("10:00:00"))).isInstanceOf(InvalidRequestException.class);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsStartNotAlignedTo15Minutes() {
        assertThatThrownBy(() -> create(at("10:05:00"), at("11:00:00"))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsEndNotAlignedTo15Minutes() {
        assertThatThrownBy(() -> create(at("10:00:00"), at("11:10:00"))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsSubSecondMisalignment() {
        Instant start = at("10:00:00").plusMillis(1);
        assertThatThrownBy(() -> create(start, at("11:00:00"))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsDurationAbove4Hours() {
        assertThatThrownBy(() -> create(at("10:00:00"), at("14:15:00"))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void acceptsExactly4Hours() {
        roomExistsAndFree();
        assertThat(create(at("10:00:00"), at("14:00:00")).getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void rejectsStartInThePast() {
        assertThatThrownBy(() -> create(at("07:45:00"), at("08:30:00"))).isInstanceOf(BusinessRuleException.class);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void acceptsStartExactlyNow() {
        roomExistsAndFree();
        assertThat(create(NOW, at("09:00:00")).getStartsAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsWhenActiveLimitReached() {
        when(reservations.countByUserIdAndStatusAndEndsAtAfter(userId, ReservationStatus.ACTIVE, NOW)).thenReturn(3L);

        assertThatThrownBy(() -> create(at("10:00:00"), at("11:00:00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("3");
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void acceptsWhenBelowActiveLimit() {
        roomExistsAndFree();
        when(reservations.countByUserIdAndStatusAndEndsAtAfter(userId, ReservationStatus.ACTIVE, NOW)).thenReturn(2L);

        assertThat(create(at("10:00:00"), at("11:00:00")).getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void rejectsUnknownRoom() {
        when(rooms.findByIdForUpdate(room.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> create(at("10:00:00"), at("11:00:00"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsOverlappingReservation() {
        when(rooms.findByIdForUpdate(room.getId())).thenReturn(Optional.of(room));
        when(reservations.existsByRoomIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                        room.getId(), ReservationStatus.ACTIVE, at("12:00:00"), at("11:00:00")))
                .thenReturn(true);

        assertThatThrownBy(() -> create(at("11:00:00"), at("12:00:00"))).isInstanceOf(ConflictException.class);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void touchingIntervalsPassTheOverlapCheck() {
        // 10h-11h existe; 11h-12h consulta "starts < 12h AND ends > 11h" (estrito), que nao casa com 10h-11h.
        roomExistsAndFree();

        create(at("10:00:00"), at("11:00:00"));
        create(at("11:00:00"), at("12:00:00"));

        verify(reservations).existsByRoomIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                room.getId(), ReservationStatus.ACTIVE, at("11:00:00"), at("10:00:00"));
        verify(reservations).existsByRoomIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                room.getId(), ReservationStatus.ACTIVE, at("12:00:00"), at("11:00:00"));
        verify(reservations, times(2)).saveAndFlush(any(Reservation.class));
    }

    // ---- cancel ----

    private Reservation existing(Instant start) {
        Reservation r = new Reservation(room, userId, start, start.plus(Duration.ofHours(1)));
        when(reservations.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void ownerCancelsMoreThan2HoursBefore() {
        Reservation r = existing(at("10:30:00"));
        assertThat(service.cancel(r.getId(), userId, false).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void ownerCancelsExactly2HoursBefore() {
        Reservation r = existing(at("10:00:00"));
        assertThat(service.cancel(r.getId(), userId, false).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void ownerCannotCancelLessThan2HoursBefore() {
        Reservation r = existing(at("09:59:00"));
        assertThatThrownBy(() -> service.cancel(r.getId(), userId, false)).isInstanceOf(BusinessRuleException.class);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void otherUserCannotCancel() {
        Reservation r = existing(at("20:00:00"));
        assertThatThrownBy(() -> service.cancel(r.getId(), UUID.randomUUID(), false))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void adminCancelsAnytimeEvenAfterStart() {
        Reservation r = existing(at("07:00:00"));
        assertThat(service.cancel(r.getId(), UUID.randomUUID(), true).getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelUnknownReservation() {
        UUID id = UUID.randomUUID();
        when(reservations.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(id, userId, false)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- listing ----

    @Test
    void listsDelegateToRepository() {
        Reservation r = new Reservation(room, userId, at("10:00:00"), at("11:00:00"));
        when(reservations.findByUserIdOrderByStartsAtDesc(userId)).thenReturn(List.of(r));
        when(reservations.findAll()).thenReturn(List.of(r));

        assertThat(service.listMine(userId)).containsExactly(r);
        assertThat(service.listAll()).containsExactly(r);
    }
}
