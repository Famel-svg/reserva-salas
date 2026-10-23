# ADR 0006: Rate limit por IP com Bucket4j em memória

Status: Aceita

## Contexto

O login (`POST /api/auth/login`) está exposto a tentativa de força bruta, e as rotas de escrita (`POST`, `PUT`, `DELETE` em `/api/**`) podem ser usadas para sobrecarregar a aplicação. É preciso limitar a taxa de requisições antes que elas cheguem aos controllers.

## Decisão

Um `OncePerRequestFilter` (`ratelimit/RateLimitFilter`) com `@Order(HIGHEST_PRECEDENCE + 10)` mantém um bucket Bucket4j (token bucket, recarga gradual em 1 minuto) por chave, em memória. `SecurityConfig` não foi alterada.

- Login: limite `app.ratelimit.login-per-minute` (padrão 10), chave `login:<IP>`.
- Escritas: limite `app.ratelimit.write-per-minute` (padrão 60), chave `write:<IP>`. O login conta só no bucket de login. `GET` não é limitado.
- Ao estourar, o filtro responde HTTP 429 com `Retry-After` (segundos) e `ProblemDetail` (RFC 9457, título "Muitas requisições"), serializado com `ObjectMapper` no próprio filtro, sem stack trace. Exceções de filtro não passam pelo `GlobalExceptionHandler`.

### Por que IP e não usuário

O filtro roda antes da autenticação, então o usuário ainda não é conhecido. No login isso é inevitável: o e-mail do corpo é dado do atacante e ainda não validado. Usar o IP também protege contra tentativas com e-mails variados.

### X-Forwarded-For

A chave usa `request.getRemoteAddr()` e ignora `X-Forwarded-For` por padrão, pois o cabeçalho é forjável: confiar nele permitiria a um cliente trocar de "IP" a cada requisição e burlar o limite. O trade-off: atrás de proxy reverso ou balanceador, todos os clientes aparecem com o IP do proxy e dividem um único bucket. Nesse cenário, habilitar `server.forward-headers-strategy` restrito a proxies confiáveis, para que `getRemoteAddr()` já venha resolvido.

## Consequências

- Memória limitada: se o mapa passar de 10.000 chaves, a próxima requisição remove os buckets sem uso há mais de 2 minutos. Com mais de 10.000 IPs ativos ao mesmo tempo o mapa pode exceder o limite temporariamente, até os buckets ficarem ociosos.
- O estado é por instância: com N réplicas o limite efetivo é N vezes maior, e reiniciar zera os contadores. Limite distribuído exigiria Redis (Bucket4j tem integração) e fica para quando houver necessidade.
- IPs compartilhados (NAT corporativo) dividem o limite.
- Os ITs usam limites altos (`AbstractPostgresIT`) porque todos logam de 127.0.0.1; os testes do filtro usam limites baixos.
