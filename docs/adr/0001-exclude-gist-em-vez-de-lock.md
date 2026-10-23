# ADR 0001: EXCLUDE USING gist sobre tstzrange em vez de lock

Status: Aceita

## Contexto

O sistema precisa impedir que duas reservas da mesma sala se sobreponham no tempo. A regra é de intervalo entre linhas diferentes: duas reservas distintas não podem ocupar o mesmo horário na mesma sala. Uma checagem em código (ler, verificar, gravar) não é segura sob concorrência, pois duas transações podem passar pela checagem ao mesmo tempo.

Alternativas consideradas:
- `@Version` (lock otimista): detecta conflito apenas na mesma linha. Duas reservas novas, em linhas diferentes, não entram em conflito, então a regra de sobreposição não é protegida.
- Lock pessimista: serializa as escritas, mas exige cuidado com deadlock e com o escopo da transação, e reduz a concorrência.

## Decisão

Usar uma constraint `EXCLUDE USING gist` sobre a coluna `tstzrange` do intervalo da reserva, com a extensão `btree_gist` para combinar igualdade no id da sala com sobreposição de intervalo. O intervalo é semiaberto `[)`, então uma reserva que termina às 10h e outra que começa às 10h não se sobrepõem.

## Consequências

- A regra fica no banco e vale para qualquer cliente, inclusive scripts e acessos fora da aplicação.
- A violação gera erro do banco que a aplicação deve traduzir para HTTP 409, o que exige um mapeamento explícito de exceção.
- Depende da extensão `btree_gist`, que precisa estar disponível e habilitada no ambiente de banco, inclusive em testes e produção.
- A regra é rígida: qualquer mudança futura que precise permitir sobreposição (por exemplo, overbooking) exigirá alterar a constraint, não apenas o código.
