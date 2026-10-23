# ADR 0010: Notificação por e-mail após o commit da reserva

Status: Aceita

## Contexto

Usuários precisam saber que a reserva foi confirmada ou cancelada. `ReservationService` publica `ReservationCreated` e `ReservationCancelled` dentro da transação. Enviar o e-mail nesse ponto avisaria sobre reservas que ainda podem sofrer rollback (por exemplo, conflito detectado pela constraint de exclusão), e uma falha de SMTP não pode desfazer uma reserva válida.

## Decisão

- Um `ReservationNotificationListener` usa `@TransactionalEventListener(phase = AFTER_COMMIT)`: só reservas efetivamente gravadas geram mensagem. Um conflito (409) não dispara envio.
- A porta `NotificationSender` tem dois adaptadores, escolhidos por `app.notifications.mode`: `log` (padrão; registra destinatário e assunto, nunca o corpo) e `smtp` (`JavaMailSender`, remetente em `app.notifications.from`).
- Com `mode=smtp` e sem `JavaMailSender` (`spring.mail.host` ausente) o app falha no startup com mensagem clara (fail fast), em vez de descobrir a falha no primeiro envio.
- O e-mail é buscado por `userId` no `UserRepository`; usuário inexistente é logado e ignorado.
- O corpo contém apenas sala, início e fim em UTC.
- A entrega é best-effort: qualquer exceção no listener é capturada e logada, sem retry, fila ou persistência de pendências. A reserva já foi commitada e o cliente não vê o erro.

## Consequências

- Uma notificação pode se perder (falha de SMTP, queda do processo entre commit e envio). Aceitável para o escopo; garantia de entrega exigiria outbox e worker de reenvio.
- O envio roda na thread da requisição, após o commit; um SMTP lento aumenta a latência da resposta. Se virar problema, o listener pode passar a `@Async`.
- Trocar de canal (SMS, push) exige apenas um novo adaptador de `NotificationSender`.
