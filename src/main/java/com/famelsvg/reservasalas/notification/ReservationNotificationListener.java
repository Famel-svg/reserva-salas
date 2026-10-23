package com.famelsvg.reservasalas.notification;

import com.famelsvg.reservasalas.reservation.event.ReservationCancelled;
import com.famelsvg.reservasalas.reservation.event.ReservationCreated;
import com.famelsvg.reservasalas.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifica o usuario apos o commit da reserva. Entrega best-effort: a reserva ja esta
 * gravada, entao qualquer falha aqui e logada e engolida (ver ADR 0010).
 */
@Component
class ReservationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationNotificationListener.class);

    private final NotificationSender sender;
    private final UserRepository users;

    ReservationNotificationListener(NotificationSender sender, UserRepository users) {
        this.sender = sender;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCreated(ReservationCreated e) {
        notify(e.userId(), "Reserva confirmada", "confirmada", e.reservationId(), e.roomId(), e.startsAt(), e.endsAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCancelled(ReservationCancelled e) {
        notify(e.userId(), "Reserva cancelada", "cancelada", e.reservationId(), e.roomId(), e.startsAt(), e.endsAt());
    }

    private void notify(UUID userId, String subject, String verb, UUID reservationId, UUID roomId, Instant start, Instant end) {
        try {
            var user = users.findById(userId).orElse(null);
            if (user == null) {
                log.warn("Notificacao ignorada: usuario {} nao encontrado (reserva {})", userId, reservationId);
                return;
            }
            String body = "Sua reserva foi " + verb + ".\nSala: " + roomId + "\nInicio (UTC): " + start + "\nFim (UTC): " + end;
            sender.send(user.getEmail(), subject, body);
        } catch (RuntimeException ex) {
            log.error("Falha ao notificar reserva {} ({}); reserva mantida", reservationId, subject, ex);
        }
    }
}
