# ADR 0003: Pacote por funcionalidade em vez de pacote por camada

Status: Aceita

## Contexto

O código Java precisa de uma organização de pacotes que facilite a leitura e a manutenção. A alternativa comum é agrupar por camada técnica (controller, service, repository), o que espalha o código de uma mesma funcionalidade por vários pacotes.

## Decisão

Organizar os pacotes por funcionalidade: `room`, `reservation`, `auth`, `user`, `security`, `common`. Cada pacote de funcionalidade reúne suas próprias classes de controller, serviço, repositório e entidades. O pacote `common` fica restrito a tratamento de erros e configuração compartilhada.

## Consequências

- Cada funcionalidade fica coesa: alterar uma regra de reserva exige navegar principalmente em `reservation`.
- O pacote `common` pode crescer sem controle se não houver disciplina. Código de negócio não deve ser movido para ele.
- Dependências entre funcionalidades (por exemplo, `reservation` usando `room`) precisam ser feitas por meio de classes públicas bem definidas, o que exige atenção para evitar acoplamento.
- Equipes acostumadas à organização por camada podem estranhar a estrutura no início.
