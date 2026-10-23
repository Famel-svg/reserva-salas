package com.famelsvg.reservasalas.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.famelsvg.reservasalas.reservation.event.ReservationCancelled;
import com.famelsvg.reservasalas.reservation.event.ReservationCreated;
import com.famelsvg.reservasalas.user.User;
import com.famelsvg.reservasalas.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReservationNotificationListenerTest {

    private final NotificationSender sender = mock(NotificationSender.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ReservationNotificationListener listener = new ReservationNotificationListener(sender, users);
    private final UUID userId = UUID.randomUUID();
    private final ReservationCreated created = new ReservationCreated(UUID.randomUUID(), UUID.randomUUID(), userId,
            Instant.parse("2030-01-01T09:00:00Z"), Instant.parse("2030-01-01T10:00:00Z"));

    private void userExists() {
        User u = mock(User.class);
        when(u.getEmail()).thenReturn("a@b.c");
        when(users.findById(userId)).thenReturn(Optional.of(u));
    }

    @Test
    void senderFailureIsSwallowed() {
        userExists();
        doThrow(new RuntimeException("smtp down")).when(sender).send(any(), any(), any());

        assertThatCode(() -> listener.onCreated(created)).doesNotThrowAnyException();
    }

    @Test
    void missingUserSkipsSend() {
        when(users.findById(userId)).thenReturn(Optional.empty());

        listener.onCreated(created);

        verifyNoInteractions(sender);
    }

    @Test
    void cancelledUsesCancelledSubjectAndUtcTimes() {
        userExists();

        listener.onCancelled(new ReservationCancelled(created.reservationId(), created.roomId(), userId,
                created.startsAt(), created.endsAt()));

        verify(sender).send(eq("a@b.c"), eq("Reserva cancelada"), contains("2030-01-01T09:00:00Z"));
    }

    @Test
    void smtpModeWithoutMailSenderFailsFast() {
        new ApplicationContextRunner()
                .withUserConfiguration(SmtpNotificationSender.class)
                .withPropertyValues("app.notifications.mode=smtp", "app.notifications.from=x@y.z")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("spring.mail.host"));
    }

    @Test
    void smtpModeWithMailHostStarts() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(SmtpNotificationSender.class)
                .withPropertyValues("app.notifications.mode=smtp", "app.notifications.from=x@y.z",
                        "spring.mail.host=localhost")
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(NotificationSender.class));
    }
}
