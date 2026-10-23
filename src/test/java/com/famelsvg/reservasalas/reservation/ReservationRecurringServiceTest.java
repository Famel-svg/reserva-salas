package com.famelsvg.reservasalas.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.famelsvg.reservasalas.common.error.BusinessRuleException;
import com.famelsvg.reservasalas.common.error.ConflictException;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ReservationRecurringServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T08:00:00Z");
    private static final Instant START = Instant.parse("2030-01-01T10:00:00Z");
    private static final Instant END = Instant.parse("2030-01-01T11:00:00Z");
    private static final Duration WEEK = Duration.ofDays(7);

    @Mock ReservationRepository reservations;
    @Mock RoomRepository rooms;

    private ReservationService service;
    private final UUID userId = UUID.randomUUID();
    private final Room room = new Room("Sala A", 10);

    @BeforeEach
    void setUp() {
        service = new ReservationService(
                reservations, rooms, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(4), Duration.ofHours(2), 3,
                Duration.ofMinutes(15), mock(ApplicationEventPublisher.class));
    }

    private void roomExists() {
        when(rooms.findByIdForUpdate(room.getId())).thenReturn(Optional.of(room));
        when(reservations.saveAndFlush(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createsWeeklyOccurrencesSharingSeriesId() {
        roomExists();

        List<Reservation> created = service.createRecurring(userId, room.getId(), START, END, 3);

        assertThat(created).extracting(Reservation::getStartsAt)
                .containsExactly(START, START.plus(WEEK), START.plus(WEEK.multipliedBy(2)));
        assertThat(created).extracting(Reservation::getEndsAt)
                .containsExactly(END, END.plus(WEEK), END.plus(WEEK.multipliedBy(2)));
        assertThat(created).extracting(Reservation::getSeriesId)
                .doesNotContainNull()
                .containsOnly(created.get(0).getSeriesId());
    }

    @Test
    void conflictInOneOccurrenceFailsAndListsItsDate() {
        roomExists();
        lenient().when(reservations.existsByRoomIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                        room.getId(), ReservationStatus.ACTIVE, END.plus(WEEK), START.plus(WEEK)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createRecurring(userId, room.getId(), START, END, 3))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Conflito nas ocorrências")
                .hasMessageContaining("2030-01-08T10:00:00Z");
    }

    @Test
    void otherRuleViolationPropagatesFromReusedCreate() {
        when(reservations.countByUserIdAndStatusAndEndsAtAfter(userId, ReservationStatus.ACTIVE, NOW)).thenReturn(3L);

        assertThatThrownBy(() -> service.createRecurring(userId, room.getId(), START, END, 2))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- cancelSeries ----

    private Reservation occurrence(UUID owner, Instant start, UUID seriesId) {
        Reservation r = new Reservation(room, owner, start, start.plus(Duration.ofHours(1)), seriesId);
        lenient().when(reservations.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void ownerCancelsOnlyOccurrencesOutsideDeadline() {
        UUID series = UUID.randomUUID();
        Reservation tooLate = occurrence(userId, NOW.plus(Duration.ofHours(1)), series);
        Reservation ok1 = occurrence(userId, NOW.plus(WEEK), series);
        Reservation ok2 = occurrence(userId, NOW.plus(WEEK.multipliedBy(2)), series);
        when(reservations.findBySeriesIdOrderByStartsAt(series)).thenReturn(List.of(tooLate, ok1, ok2));

        assertThat(service.cancelSeries(series, userId, false)).isEqualTo(2);
        assertThat(tooLate.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(ok1.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(ok2.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void adminCancelsFutureOccurrencesAndSkipsPastAndCancelled() {
        UUID series = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Reservation soon = occurrence(owner, NOW.plus(Duration.ofHours(1)), series);
        Reservation past = occurrence(owner, NOW.minus(WEEK), series);
        Reservation already = occurrence(owner, NOW.plus(WEEK), series);
        already.cancel();
        when(reservations.findBySeriesIdOrderByStartsAt(series)).thenReturn(List.of(past, soon, already));

        assertThat(service.cancelSeries(series, UUID.randomUUID(), true)).isEqualTo(1);
        assertThat(soon.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(past.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void seriesWithoutOccurrencesOfTheUserIs404() {
        UUID series = UUID.randomUUID();
        Reservation foreign = occurrence(UUID.randomUUID(), NOW.plus(WEEK), series);
        when(reservations.findBySeriesIdOrderByStartsAt(series)).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service.cancelSeries(series, userId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
