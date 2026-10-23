package com.famelsvg.reservasalas.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Adaptador padrão: registra destinatário e assunto. O corpo nunca é logado. */
@Component
@ConditionalOnProperty(name = "app.notifications.mode", havingValue = "log", matchIfMissing = true)
class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Notificacao (modo log) para={} assunto={}", to, subject);
    }
}
