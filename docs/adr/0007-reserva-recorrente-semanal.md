# ADR 0007: Reserva recorrente semanal, tudo ou nada

Status: Aceita

## Contexto

Usuários querem reservar a mesma sala no mesmo horário em semanas consecutivas sem repetir o pedido. A criação avulsa (`ReservationService.create`) já concentra todas as regras: intervalo, alinhamento de 15 minutos, duração máxima, passado, limite de reservas ativas e conflito. Duplicar essas regras para a recorrência criaria dois caminhos a manter em sincronia.

## Decisão

- `POST /api/reservations/recurring` recebe `{roomId, startsAt, endsAt, occurrences}` com `occurrences` entre 2 e 3 (Bean Validation) e cria uma reserva por semana: a primeira em `startsAt/endsAt`, as demais somando 7 dias.
- Um único método `@Transactional` chama o `create` existente para cada ocorrência, então nenhuma regra é duplicada. Qualquer falha desfaz a transação inteira (tudo ou nada) e o erro original é devolvido. Conflitos (409) são acumulados para o `detail` listar todas as datas em conflito; as demais falhas (limite, passado etc.) propagam imediatamente.
- Todas as ocorrências compartilham um `series_id` (uuid, nullable, migration V5); reservas avulsas ficam com `null`. A resposta é 201 com a lista de `ReservationResponse`, que ganha o campo `seriesId`.
- `DELETE /api/reservations/series/{seriesId}` cancela, via `ReservationService.cancel` (que publica `ReservationCancelled` por ocorrência), as ocorrências ACTIVE futuras que o usuário pode cancelar. O dono cancela até 2 h antes de cada ocorrência; as que já estão dentro do prazo são ignoradas, sem falhar a série. ADMIN cancela todo o futuro. Sem nenhuma ocorrência do usuário na série, responde 404. Devolve 200 com a quantidade cancelada.
- Cada ocorrência conta no limite de reservas ativas por usuário, pois reutiliza o `create`. Com o limite padrão de 3, uma recorrência de 3 ocorrências consome todo o limite, e o usuário só reserva de novo quando alguma terminar ou for cancelada.

## Consequências

- A recorrência máxima prática é limitada pelo limite de reservas ativas (3 por padrão). Aceitamos isso porque o limite existe para impedir que um usuário monopolize salas; deixar a recorrência contorná-lo reabriria exatamente esse problema. Quem precisar de séries maiores deve ter o limite ajustado por configuração, não uma exceção na recorrência.
- Regras novas na criação avulsa valem automaticamente para a recorrência.
- Não há edição de série nem recorrência além de semanal; cancelar e recriar cobre esses casos por ora.
- Uma série é criada com a sala bloqueada (`findByIdForUpdate`) a cada ocorrência dentro da mesma transação; séries ficam curtas (máximo 3), então o tempo de lock é pequeno.
- Cancelar a série não é atômico por regra de prazo: ocorrências dentro das 2 h permanecem ativas e o cliente vê a quantidade cancelada para perceber isso.
