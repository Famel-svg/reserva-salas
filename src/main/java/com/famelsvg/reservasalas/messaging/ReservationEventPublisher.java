package com.famelsvg.reservasalas.messaging;

import static com.famelsvg.reservasalas.config.RabbitConfig.EXCHANGE;
import static com.famelsvg.reservasalas.config.RabbitConfig.KEY_CANCELLED;
import static com.famelsvg.reservasalas.config.RabbitConfig.KEY_CREATED;

import com.famelsvg.reservasalas.reservation.event.ReservationCancelled;
import com.famelsvg.reservasalas.reservation.event.ReservationCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica os eventos de reserva no RabbitMQ somente após o commit (rollback não publica nada).
 * Entrega at-most-once: se o broker falhar, o erro é logado e engolido, pois a reserva já foi
 * commitada e a requisição não pode falhar por isso (ver ADR 0009).
 */
@Component
public class ReservationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventPublisher.class);

    private final RabbitTemplate rabbit;

    public ReservationEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReservationCreated event) {
        publish(KEY_CREATED, event, event.reservationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReservationCancelled event) {
        publish(KEY_CANCELLED, event, event.reservationId());
    }

    private void publish(String routingKey, Object event, Object reservationId) {
        try {
            rabbit.convertAndSend(EXCHANGE, routingKey, event);
        } catch (AmqpException e) {
            log.error("Falha ao publicar {} da reserva {}; evento perdido (at-most-once)", routingKey, reservationId, e);
        }
    }
}
