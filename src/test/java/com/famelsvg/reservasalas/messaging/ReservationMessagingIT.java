package com.famelsvg.reservasalas.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.famelsvg.reservasalas.it.AbstractPostgresIT;
import com.famelsvg.reservasalas.reservation.event.ReservationCancelled;
import com.famelsvg.reservasalas.reservation.event.ReservationCreated;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

class ReservationMessagingIT extends AbstractPostgresIT {

    private static final long WAIT_MS = 5000;

    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;

    /** Fila exclusiva do teste, ligada à exchange só na routing key pedida. */
    private String bind(String routingKey) {
        String name = "test-" + UUID.randomUUID();
        Queue q = new Queue(name, false, false, true);
        admin.declareQueue(q);
        admin.declareBinding(BindingBuilder.bind(q).to(new TopicExchange("reservas", true, false)).with(routingKey));
        return name;
    }

    private String createReservation(String token, UUID room, int day) throws Exception {
        return JsonPath.read(reserve(token, room, at(day, 10), at(day, 11))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void createPublishesReservaCriada() throws Exception {
        String queue = bind("reserva.criada");
        UUID room = createRoom(newAdmin());

        String id = createReservation(newMember(), room, 20);

        ReservationCreated event = rabbit.receiveAndConvert(queue, WAIT_MS,
                new ParameterizedTypeReference<ReservationCreated>() {});
        assertThat(event).isNotNull();
        assertThat(event.reservationId()).hasToString(id);
        assertThat(event.roomId()).isEqualTo(room);
        assertThat(event.startsAt()).isEqualTo(at(20, 10));
    }

    @Test
    void conflictPublishesNothing() throws Exception {
        UUID room = createRoom(newAdmin());
        createReservation(newMember(), room, 21);
        String queue = bind("reserva.criada"); // ligada depois da 1a reserva: so veria a do conflito

        reserve(newMember(), room, at(21, 10), at(21, 11)).andExpect(status().isConflict());

        assertThat(rabbit.receive(queue, 1500)).isNull();
    }

    @Test
    void cancelPublishesReservaCancelada() throws Exception {
        String queue = bind("reserva.cancelada");
        String member = newMember();
        String id = createReservation(member, createRoom(newAdmin()), 22);

        cancel(member, id).andExpect(status().is2xxSuccessful());

        ReservationCancelled event = rabbit.receiveAndConvert(queue, WAIT_MS,
                new ParameterizedTypeReference<ReservationCancelled>() {});
        assertThat(event).isNotNull();
        assertThat(event.reservationId()).hasToString(id);
    }
}
