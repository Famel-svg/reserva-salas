# ADR 0009: Eventos de reserva no RabbitMQ, publicados após o commit

Status: Aceita

## Contexto

Outros sistemas (notificações, relatórios) precisam saber quando uma reserva é criada ou cancelada. O `ReservationService` já publica `ReservationCreated` e `ReservationCancelled` como eventos de aplicação dentro da transação. Publicar no broker nesse ponto anunciaria reservas que podem sofrer rollback (por exemplo, conflito detectado pela constraint de exclusão).

## Decisão

- Um `ReservationEventPublisher` escuta os eventos com `@TransactionalEventListener(phase = AFTER_COMMIT)`: rollback não publica nada.
- Exchange `topic` durável `reservas`, com as routing keys `reserva.criada` e `reserva.cancelada`, declarada em `RabbitConfig`.
- Payload em JSON (`Jackson2JsonMessageConverter` com o `ObjectMapper` da aplicação): o próprio record do evento, com datas em ISO-8601.
- Falha ao publicar (broker fora do ar) é capturada e logada; a requisição segue com sucesso, pois a reserva já está commitada.
- Não há consumidores de negócio nesta fatia; as filas são responsabilidade de quem consome.

## Consequências

- Entrega **at-most-once**: se o broker estiver indisponível ou a JVM cair entre o commit e o envio, o evento é perdido sem retentativa. Consumidores não devem tratar a fila como fonte da verdade; o banco é.
- Um outbox transacional (gravar o evento na mesma transação e despachá-lo por um relay) daria at-least-once, mas exige tabela, relay, idempotência nos consumidores e limpeza. Fica para a evolução, quando houver um consumidor que não tolere perdas.
- Sem o broker no ar, o único sinal é o log de erro; o publisher não tem métrica nem alerta próprios.
- O publisher roda no thread da requisição, após o commit, então adiciona a latência do envio à resposta.
