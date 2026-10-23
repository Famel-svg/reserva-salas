package com.famelsvg.reservasalas.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.famelsvg.reservasalas.reservation.event.ReservationCancelled;
import com.famelsvg.reservasalas.reservation.event.ReservationCreated;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class ReservationEventPublisherTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final ReservationEventPublisher publisher = new ReservationEventPublisher(rabbit);

    @Test
    void brokerDownDoesNotPropagate() {
        doThrow(new AmqpConnectException(new RuntimeException("broker fora")))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));
        Instant t = Instant.now();
        UUID id = UUID.randomUUID();

        assertDoesNotThrow(() -> publisher.on(new ReservationCreated(id, id, id, t, t)));
        assertDoesNotThrow(() -> publisher.on(new ReservationCancelled(id, id, id, t, t)));
    }

    /**
     * Rollback nao publica nada porque o listener so dispara apos o commit. Se alguem trocar a fase
     * (ou remover a anotacao), o evento sairia antes do commit e este teste quebra.
     */
    @Test
    void listenersRunOnlyAfterCommit() throws Exception {
        for (Class<?> event : new Class<?>[] {ReservationCreated.class, ReservationCancelled.class}) {
            TransactionalEventListener annotation = ReservationEventPublisher.class
                    .getMethod("on", event).getAnnotation(TransactionalEventListener.class);
            assertThat(annotation).as("listener de %s", event.getSimpleName()).isNotNull();
            assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
            assertThat(annotation.fallbackExecution()).isFalse();
        }
    }
}
