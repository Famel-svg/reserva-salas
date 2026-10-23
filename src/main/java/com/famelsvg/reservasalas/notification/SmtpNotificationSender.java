package com.famelsvg.reservasalas.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Adaptador SMTP. Sem JavaMailSender (spring.mail.host ausente) o app falha no startup. */
@Component
@ConditionalOnProperty(name = "app.notifications.mode", havingValue = "smtp")
class SmtpNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpNotificationSender(ObjectProvider<JavaMailSender> mailSender, @Value("${app.notifications.from}") String from) {
        this.mailSender = mailSender.getIfAvailable();
        if (this.mailSender == null) {
            throw new IllegalStateException(
                    "app.notifications.mode=smtp exige um JavaMailSender: configure spring.mail.host (e demais spring.mail.*)");
        }
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }
}
