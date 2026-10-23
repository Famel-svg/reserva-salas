# Duas pessoas nunca deveriam reservar a mesma sala no mesmo horário: esta API garante isso no banco.

[![CI](https://github.com/Famel-svg/reserva-salas/actions/workflows/ci.yml/badge.svg)](https://github.com/Famel-svg/reserva-salas/actions/workflows/ci.yml)
![Cobertura](https://img.shields.io/badge/cobertura-%E2%89%A580%25%20(JaCoCo)-brightgreen)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)

API REST de reserva de salas compartilhadas, sem sobreposição de horário, com autenticação JWT e papéis `MEMBER` e `ADMIN`.

![Swagger UI com os endpoints da API](docs/img/swagger-ui.png)

## Por que este projeto existe

É um projeto de portfólio para vagas júnior de Java. O objetivo é mostrar, num código pequeno e testado, decisões que costumam dar errado em sistemas reais:

- **Concorrência de intervalo de tempo.** Duas reservas da mesma sala no mesmo horário são um problema de corrida. A regra fica garantida pelo banco com `EXCLUDE USING gist` sobre `tstzrange`, e não em um `if` no código.
- **Autorização por dono do recurso.** Um usuário não cancela reserva alheia. Isso evita IDOR (retornar 403 e não 404 quando o recurso existe, mas não é seu).
- **Erros padronizados.** Todas as falhas saem em ProblemDetail (RFC 9457), sem stack trace.
- **Testes que provam a regra.** Inclui um teste de concorrência com 20 threads, em que exatamente uma reserva passa.

## Funcionalidades

- CRUD de salas, restrito a `ADMIN`. As leituras de sala ficam em cache no Redis por 10 minutos e são invalidadas a cada alteração.
- Reserva com início e fim em UTC (`timestamptz`), granularidade de 15 minutos, duração máxima de 4 h e sem início no passado.
- Horários encostados (10h–11h e 11h–12h) são aceitos. O intervalo é semiaberto `[)`.
- Reserva recorrente semanal (2 ou 3 ocorrências, tudo ou nada), com cancelamento da série inteira.
- Cancelamento: o dono cancela até 2 h antes do início; `ADMIN` cancela a qualquer hora; outro usuário recebe 403.
- Limite de 3 reservas ativas por usuário (422 ao passar). Cada ocorrência de uma recorrência conta.
- Login com senha BCrypt, access token JWT de 15 min e refresh token com rotação. Reuso de refresh token revoga todas as sessões do usuário.
- Só `ADMIN` cadastra usuários. Não há cadastro público.
- Rate limit por IP: 10 logins por minuto e 60 escritas por minuto. Excesso devolve 429 com `Retry-After`.
- Após criar ou cancelar, um e-mail é enviado (modo `log` por padrão, ou `smtp`) e o evento vai para o RabbitMQ (exchange `reservas`, chaves `reserva.criada` e `reserva.cancelada`). Os dois acontecem só após o commit.

## Como a não sobreposição é garantida

```mermaid
sequenceDiagram
    autonumber
    participant A as Cliente A
    participant B as Cliente B
    participant API as ReservationService
    participant DB as PostgreSQL

    A->>API: POST /api/reservations (sala X, 10h-11h)
    B->>API: POST /api/reservations (sala X, 10h-11h)
    API->>DB: SELECT room FOR UPDATE (fila por sala)
    DB-->>API: lock concedido a A
    API->>DB: checa conflito e INSERT (A)
    DB-->>API: ok, EXCLUDE aceita
    API-->>A: 201 Created
    API->>DB: SELECT room FOR UPDATE
    DB-->>API: lock liberado para B
    API->>DB: checa conflito, encontra A
    API-->>B: 409 Conflict
```

A EXCLUDE é a garantia final e vale mesmo se alguém acessar o banco sem passar pela API. O lock por sala evita que duas transações concorrentes entrem em deadlock na EXCLUDE (veja o [ADR 0001](docs/adr/0001-exclude-gist-em-vez-de-lock.md)).

## Quick start

Pré-requisitos: Docker com Compose. Não é preciso Java nem Maven.

```bash
cp .env.example .env
```

Gere um segredo JWT e cole no `.env` no lugar de `JWT_SECRET`:

```bash
openssl rand -base64 48
```

Suba app e banco:

```bash
docker compose up --build
```

Em outro terminal, confira o health e faça login com o admin de dev (definido no `.env`):

```bash
curl -s http://localhost:8080/health
```

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@reserva.local","password":"troque-esta-senha-de-dev"}'
```

Use o `accessToken` retornado no cabeçalho `Authorization: Bearer <token>`. Você também pode testar tudo pelo Swagger em <http://localhost:8080/swagger-ui.html>.

Este quick start foi executado do zero (stack parado, `docker compose down -v`, depois `up --build`) e respondeu `/health` com 200.

## Veja o 409 na prática

Duas reservas no mesmo horário, na mesma sala. A segunda recebe 409:

```bash
# primeira reserva: 201
curl -s -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"roomId":"<id-da-sala>","startsAt":"2026-10-24T10:00:00Z","endsAt":"2026-10-24T11:00:00Z"}'

# sobreposição parcial: 409
curl -s -w '\nHTTP %{http_code}\n' -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"roomId":"<id-da-sala>","startsAt":"2026-10-24T10:30:00Z","endsAt":"2026-10-24T11:30:00Z"}'
```

Resposta real da segunda chamada:

```json
{"type":"about:blank","title":"Conflito","status":409,"detail":"Sala já reservada neste horário","instance":"/api/reservations"}
HTTP 409
```

## Teste de concorrência

Vinte threads disparam reservas no mesmo horário e na mesma sala, com largada simultânea. Saída real do teste:

```
CONCURRENCY RESULT: 201=1 409=19 other=[]
```

Para rodar a suíte completa (precisa de Docker para o Testcontainers):

```bash
JWT_SECRET=ci-only-secret-com-mais-de-32-bytes-0123456789 ./mvnw verify
```

A suíte tem 106 testes: unitários de regra de negócio, `@WebMvcTest` de segurança e testes ponta a ponta com PostgreSQL 16 real. O gate JaCoCo exige 80% de linhas e o projeto está em cerca de 91%.

## Front-end

Interface em React (Vite + TypeScript) na pasta [`frontend/`](frontend/). Depois de `docker compose up --build`, ela fica em <http://localhost:3000>.

![Visão geral do front-end: cartões de resumo e agenda de hoje por sala](docs/img/dashboard-desktop.png)

Outras telas: [reservas, tema escuro](docs/img/dashboard-reservas.png) e [celular, 375 px](docs/img/dashboard-mobile.png).

- Login com renovação automática do access token (usa o refresh token em caso de 401).
- Salas: lista para todos; criar, editar e remover só para `ADMIN`.
- Visão geral: cartões de resumo (salas, reservas hoje, minhas próximas, ativas no total para `ADMIN`) e agenda do dia 08h–20h UTC por sala, calculados no cliente.
- Reservas: formulário com validação de 15 min e início antes do fim, lista e cancelamento.
- Recorrência semanal e cancelamento de série; `ADMIN` cadastra usuários.
- Erros do backend (ProblemDetail e 429 com `Retry-After`) aparecem na tela.
- Remover sala e cancelar reserva ou série pedem confirmação no próprio botão.
- Tema claro e escuro (segue o sistema), contraste AA e layout de uma coluna no celular.

Para desenvolver sem Docker: `cd frontend && npm ci && npm run dev` (proxy de `/api` para `localhost:8080`). Testes: `npm test -- --run`.

## Endpoints por papel

| Método | Rota | Público | MEMBER | ADMIN |
|---|---|:-:|:-:|:-:|
| POST | `/api/auth/login` | sim | | |
| POST | `/api/auth/refresh` | sim | | |
| POST | `/api/auth/logout` | sim | | |
| GET | `/health` | sim | | |
| GET | `/api/rooms` | | sim | sim |
| GET | `/api/rooms/{id}` | | sim | sim |
| POST | `/api/rooms` | | | sim |
| PUT | `/api/rooms/{id}` | | | sim |
| DELETE | `/api/rooms/{id}` | | | sim |
| POST | `/api/users` | | | sim |
| POST | `/api/reservations` | | sim | sim |
| POST | `/api/reservations/recurring` | | sim | sim |
| GET | `/api/reservations/mine` | | sim | sim |
| GET | `/api/reservations` | | | sim |
| DELETE | `/api/reservations/{id}` | | dono, até 2 h antes | sim, qualquer hora |
| DELETE | `/api/reservations/series/{seriesId}` | | dono, por ocorrência até 2 h antes | sim, qualquer hora |

Documentação interativa: <http://localhost:8080/swagger-ui.html>.

## Decisões de arquitetura

As decisões estão em [`docs/adr`](docs/adr):

1. [EXCLUDE gist em vez de `@Version` ou lock pessimista](docs/adr/0001-exclude-gist-em-vez-de-lock.md)
2. [JWT stateless sem refresh](docs/adr/0002-jwt-stateless-sem-refresh.md) (substituído pelo 0005)
3. [Pacote por funcionalidade](docs/adr/0003-pacote-por-funcionalidade.md)
4. [ProblemDetail para erros](docs/adr/0004-problemdetail-para-erros.md)
5. [Refresh token com rotação](docs/adr/0005-refresh-token-com-rotacao.md)
6. [Rate limit por IP](docs/adr/0006-rate-limit-por-ip.md)
7. [Reserva recorrente semanal](docs/adr/0007-reserva-recorrente-semanal.md)
8. [Cache de salas no Redis](docs/adr/0008-cache-de-salas-redis.md)
9. [Mensageria com RabbitMQ](docs/adr/0009-mensageria-rabbitmq.md)
10. [Notificação por e-mail após o commit](docs/adr/0010-notificacao-por-email-apos-commit.md)

O histórico de cada commit está em [`docs/CHANGELOG.md`](docs/CHANGELOG.md).

## Configuração

Tudo vem de variáveis de ambiente. Veja [`.env.example`](.env.example).

| Variável | Obrigatória | Descrição |
|---|:-:|---|
| `JWT_SECRET` | sim | Segredo HS256 de no mínimo 32 bytes. Não tem valor padrão: o app não sobe sem ele. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | não | Conexão com o PostgreSQL. |
| `SPRING_PROFILES_ACTIVE=dev` + `ADMIN_EMAIL` + `ADMIN_PASSWORD` | não | Cria o admin inicial só no perfil `dev`. Nunca em migration. |
| `JWT_TTL` e `REFRESH_TTL` | não | Duração do access token (padrão `PT15M`) e do refresh token (padrão `P7D`). |
| `REDIS_HOST`, `REDIS_PORT` | não | Redis do cache de salas. |
| `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD` | não | Broker dos eventos de reserva. |
| `NOTIFICATION_MODE` | não | `log` (padrão, só registra no console) ou `smtp` (usa `spring.mail.*`; falha no startup se o SMTP não estiver configurado). |
| `NOTIFICATION_FROM` | não | Remetente dos e-mails. |

O actuator expõe somente `/health`.

## O que eu faria diferente

- **Lock por sala como gargalo.** Hoje a criação de reservas da mesma sala é serializada. Para uma sala muito disputada, eu mediria antes de decidir entre manter isso ou partir para uma fila ou um controle de capacidade por slot.
- **Cancelar não é idempotente.** Cancelar uma reserva já cancelada devolve 200 de novo. Eu deixaria explícito (409 ou 204 com regra clara).
- **Apagar sala com reservas dá 409.** A chave estrangeira protege, mas o usuário merece uma mensagem clara. Eu marcaria a sala como inativa em vez de apagar.
- **Login revela tempo de resposta.** A mensagem de erro é igual para e-mail inexistente e senha errada, mas o BCrypt só roda quando o usuário existe. Eu igualaria o tempo com um hash fictício.
- **Access token não é revogável.** Logout e reuso revogam o refresh token, mas o access token segue válido até o TTL de 15 min. Para revogação imediata eu guardaria uma lista de tokens revogados consultada por requisição.
- **Limite de reservas e recorrência brigam entre si.** Com o limite padrão de 3, uma recorrência de 3 semanas só passa se o usuário não tiver nenhuma outra reserva ativa. Eu separaria o limite por série ou aumentaria o padrão com uma regra explícita.
- **Extensão `btree_gist` na migration.** Exige privilégio de superuser ou extensão confiável no ambiente. Em produção eu separaria essa etapa do Flyway.

## Ainda fora do escopo

Estas evoluções ficaram de fora desta versão, de propósito:

- Outbox transacional para eventos, para garantir entrega mesmo com o broker fora do ar (hoje a entrega é at-most-once, ver ADR 0009).
- Limpeza periódica de refresh tokens expirados (ADR 0005).
- Rate limit distribuído. Hoje o contador é por instância (ADR 0006).

