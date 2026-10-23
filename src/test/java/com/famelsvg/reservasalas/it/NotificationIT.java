package com.famelsvg.reservasalas.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.famelsvg.reservasalas.notification.NotificationSender;
import com.famelsvg.reservasalas.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Fica em {@code it} porque AbstractPostgresIT e package-private.
 * Fluxo real (HTTP + Postgres) com um sender capturador no lugar do adaptador de log.
 */
@Import(NotificationIT.Capture.class)
class NotificationIT extends AbstractPostgresIT {

    record Sent(String to, String subject, String body) {}

    @TestConfiguration
    static class Capture {
        static final List<Sent> SENT = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        NotificationSender capturingSender() {
            return (to, subject, body) -> SENT.add(new Sent(to, subject, body));
        }
    }

    @Autowired UserRepository users;

    @BeforeEach
    void clear() { Capture.SENT.clear(); }

    private String emailOf(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        UUID id = UUID.fromString(JsonPath.read(payload, "$.sub"));
        return users.findById(id).orElseThrow().getEmail();
    }

    @Test
    void createSendsExactlyOneEmailAndCancelSendsAnother() throws Exception {
        String member = newMember();
        UUID room = createRoom(newAdmin());
        String reservationId = JsonPath.read(
                reserve(member, room, at(10, 9), at(10, 10)).andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(Capture.SENT).hasSize(1);
        assertThat(Capture.SENT.get(0).to()).isEqualTo(emailOf(member));
        assertThat(Capture.SENT.get(0).subject()).isEqualTo("Reserva confirmada");
        assertThat(Capture.SENT.get(0).body()).contains(room.toString());

        cancel(member, reservationId);

        assertThat(Capture.SENT).hasSize(2);
        assertThat(Capture.SENT.get(1).subject()).isEqualTo("Reserva cancelada");
    }

    @Test
    void conflictSendsNothing() throws Exception {
        UUID room = createRoom(newAdmin());
        reserve(newMember(), room, at(11, 9), at(11, 10));
        Capture.SENT.clear();

        int status = reserve(newMember(), room, at(11, 9), at(11, 10)).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(409);
        assertThat(Capture.SENT).isEmpty();
    }
}
