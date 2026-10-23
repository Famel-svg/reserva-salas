# ADR 0002: JWT stateless com access token curto, sem refresh token

Status: Substituída pelo ADR 0005

## Contexto

A API precisa autenticar usuários. Para o escopo do MVP, é preciso uma solução simples de implementar e de operar, sem armazenamento de sessão no servidor. Refresh tokens exigiriam persistência, rotação e controle de revogação, o que aumenta o escopo desta versão.

## Decisão

Usar JWT stateless como access token com validade de 15 minutos. Não emitir refresh token nesta versão. Quando o token expirar, o cliente faz login novamente. Refresh token está registrado na lista de evolução.

## Consequências

- Não existe revogação imediata: um token emitido continua válido até expirar. Por isso o TTL é curto, limitando a janela de uso indevido.
- O usuário precisa autenticar novamente a cada 15 minutos, o que piora a experiência de uso até a entrega do refresh token.
- Não há estado no servidor para autenticação, o que simplifica a escala horizontal e a implantação.
- A adição futura de refresh token exigirá armazenamento e rotação, e deve ser planejada como mudança separada.
