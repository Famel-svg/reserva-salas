# Changelog de desenvolvimento

Registro curto do que cada commit entrega, em ordem cronológica.

## Unreleased

- **chore: esqueleto Spring Boot 3.5 com Java 21.** Pom com starters de web, validação, JPA, segurança, resource server, actuator, Flyway, PostgreSQL, springdoc e Testcontainers. JaCoCo com gate de 80% de linhas na fase `verify`. Tratamento de erros em ProblemDetail (RFC 9457), sem stack trace. `application.yml` exige `JWT_SECRET` sem valor padrão. Actuator expõe só `/health`.
- **docs: quatro ADRs curtos.** Registra as decisões de EXCLUDE gist em vez de lock, JWT stateless sem refresh, pacote por funcionalidade e ProblemDetail para erros (`docs/adr/`).
- **feat(auth): login com BCrypt e JWT de acesso curto.** `POST /api/auth/login` devolve token HS256 com `sub` = id do usuário e claim `role`. Segredo vem só de `JWT_SECRET` e o app falha no startup se tiver menos de 32 bytes. Papéis MEMBER e ADMIN. Admin inicial criado só no perfil `dev`, por `ADMIN_EMAIL` e `ADMIN_PASSWORD`. Erros de corpo inválido viram 400 e credencial errada vira 401, ambos em ProblemDetail.
- **feat(reservas): salas e reservas com regras de negócio.** CRUD de salas só para ADMIN. Reserva com início antes do fim, alinhamento de 15 minutos, duração máxima de 4 h, sem início no passado, limite de reservas ativas por usuário, dono cancela até 2 h antes e ADMIN cancela a qualquer hora. Sobreposição é impedida pela EXCLUDE gist sobre `tstzrange` (V3), com horários encostados permitidos.
- **fix(reservas): serializa a criação por sala para evitar deadlock.** `SELECT ... FOR UPDATE` na linha da sala antes da checagem de conflito. Sem isso, 20 inserts simultâneos davam deadlock no Postgres e viravam 500. ADR 0001 atualizado.
- **feat(users): ADMIN cadastra usuários.** `POST /api/users` cria MEMBER ou ADMIN com senha em BCrypt. E-mail repetido vira 409. Sem isso o papel MEMBRO não tinha como existir pela API.
