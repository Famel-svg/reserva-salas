# Changelog de desenvolvimento

Registro curto do que cada commit entrega, em ordem cronológica.

## Unreleased

- **chore: esqueleto Spring Boot 3.5 com Java 21.** Pom com starters de web, validação, JPA, segurança, resource server, actuator, Flyway, PostgreSQL, springdoc e Testcontainers. JaCoCo com gate de 80% de linhas na fase `verify`. Tratamento de erros em ProblemDetail (RFC 9457), sem stack trace. `application.yml` exige `JWT_SECRET` sem valor padrão. Actuator expõe só `/health`.
- **docs: quatro ADRs curtos.** Registra as decisões de EXCLUDE gist em vez de lock, JWT stateless sem refresh, pacote por funcionalidade e ProblemDetail para erros (`docs/adr/`).
- **feat(auth): login com BCrypt e JWT de acesso curto.** `POST /api/auth/login` devolve token HS256 com `sub` = id do usuário e claim `role`. Segredo vem só de `JWT_SECRET` e o app falha no startup se tiver menos de 32 bytes. Papéis MEMBER e ADMIN. Admin inicial criado só no perfil `dev`, por `ADMIN_EMAIL` e `ADMIN_PASSWORD`. Erros de corpo inválido viram 400 e credencial errada vira 401, ambos em ProblemDetail.
