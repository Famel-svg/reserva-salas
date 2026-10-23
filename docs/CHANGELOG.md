# Changelog de desenvolvimento

Registro curto do que cada commit entrega, em ordem cronológica.

## Unreleased

- **chore: esqueleto Spring Boot 3.5 com Java 21.** Pom com starters de web, validação, JPA, segurança, resource server, actuator, Flyway, PostgreSQL, springdoc e Testcontainers. JaCoCo com gate de 80% de linhas na fase `verify`. Tratamento de erros em ProblemDetail (RFC 9457), sem stack trace. `application.yml` exige `JWT_SECRET` sem valor padrão. Actuator expõe só `/health`.
- **docs: quatro ADRs curtos.** Registra as decisões de EXCLUDE gist em vez de lock, JWT stateless sem refresh, pacote por funcionalidade e ProblemDetail para erros (`docs/adr/`).
