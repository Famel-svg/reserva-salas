# ADR 0004: ProblemDetail (RFC 9457) como formato único de erro

Status: Aceita

## Contexto

A API precisa devolver erros de forma consistente para que o cliente trate cada caso sem depender de formatos ad hoc. Sem padrão, cada controller tende a montar seu próprio corpo de erro, e stack traces podem vazar para o cliente.

## Decisão

Adotar `ProblemDetail` (RFC 9457) como formato único de erro, centralizado em um `@RestControllerAdvice`. O status HTTP é explícito por tipo de erro: 400, 401, 403, 404, 409 e 422. Respostas de erro nunca incluem stack trace.

## Consequências

- O contrato de erro é padronizado e documentado por um único ponto de tratamento, o que facilita o consumo pelo cliente.
- Cada nova exceção de domínio precisa de um mapeamento explícito; exceções não mapeadas caem em um erro genérico, que deve ser monitorado.
- A distinção entre 400 e 422 exige critério consistente da equipe: 400 para requisição malformada, 422 para regra de negócio violada.
- O formato RFC 9457 é mais verboso que um corpo simples de erro, e exige cuidado para não expor detalhes internos nos campos `detail` e `title`.
