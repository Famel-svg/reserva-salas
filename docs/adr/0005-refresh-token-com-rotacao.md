# ADR 0005: Refresh token opaco com rotação e logout

Status: Aceita

Substitui o ADR 0002 no ponto "sem refresh token".

## Contexto

O access token JWT dura 15 minutos e não pode ser revogado. Sem refresh token, o usuário precisa digitar a senha a cada 15 minutos e não existe logout real. A evolução prevista no ADR 0002 era justamente adicionar refresh com rotação.

## Decisão

- O login devolve, além do access token, um refresh token opaco: 256 bits aleatórios (SecureRandom) em Base64 URL, enviado ao cliente uma única vez.
- No banco fica só o SHA-256 do token (`refresh_token.token_hash`, único). Como o token tem alta entropia, um hash rápido basta e permite busca direta por índice; um vazamento do banco não entrega tokens utilizáveis.
- `POST /api/auth/refresh` revoga o token apresentado (`revoked_at`) e emite um novo par (rotação). A revogação é um UPDATE condicional (`revoked_at is null`), então duas requisições simultâneas com o mesmo token não geram dois pares válidos.
- Apresentar um token já revogado é tratado como reuso (possível roubo): todos os refresh tokens ativos do usuário são revogados e a resposta é 401. A transação usa `noRollbackFor` para que essa revogação não seja desfeita pela exceção.
- Token expirado (`expires_at` anterior ao instante do `Clock` injetado) ou inexistente responde 401 ProblemDetail. A validade é `app.refresh.ttl` (padrão P7D).
- `POST /api/auth/logout` revoga o token e responde 204, inclusive para token desconhecido, para não revelar quais tokens existem.
- `/refresh` e `/logout` são públicos: a credencial é o próprio refresh token.

## Consequências

- Passa a existir estado de autenticação no servidor (tabela `refresh_token`), que cresce a cada login e refresh. A limpeza de tokens expirados fica como evolução.
- O access token continua sem revogação imediata; logout e detecção de reuso só impedem renovações. A janela máxima de uso indevido segue sendo o TTL de 15 minutos.
- Um cliente legítimo que perde a resposta de um refresh e tenta de novo com o token antigo derruba todas as suas sessões e precisa fazer login novamente. É o custo aceito da detecção de reuso.
- A resposta de login ganha o campo `refreshToken`; clientes devem guardá-lo com o mesmo cuidado de uma senha.
