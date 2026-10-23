package com.famelsvg.reservasalas.notification;

/** Porta de saída de notificações; os adaptadores escolhem o canal (log ou SMTP). */
public interface NotificationSender {

    void send(String to, String subject, String body);
}
