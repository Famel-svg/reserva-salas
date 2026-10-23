# Frontend – Reserva de Salas

React 18 + TypeScript + Vite. Consome a API Spring Boot da raiz do repositório.

## Desenvolvimento

```bash
# terminal 1: backend na porta 8080 (ver README da raiz)
cd frontend
npm ci
npm run dev        # http://localhost:5173, /api é proxy para localhost:8080
npm test -- --run  # testes (Vitest + Testing Library)
npm run build      # typecheck + build em dist/
```

## Docker Compose

Na raiz do repositório (com `.env` configurado):

```bash
docker compose up --build
```

A interface fica em http://localhost:3000. O nginx serve o SPA e faz proxy de `/api/` para `app:8080`.

## Tokens e sessionStorage (trade-off)

`accessToken` e `refreshToken` ficam em memória e em `sessionStorage`, para a sessão sobreviver a F5.
Vantagem: simples e some ao fechar a aba. Desvantagem: qualquer XSS na página consegue ler os tokens.
Alternativa mais segura: refresh token em cookie `HttpOnly` + `SameSite`, o que exige mudar o backend.
Aceitável neste projeto; o backend continua sendo a autoridade sobre permissões (a UI só esconde botões).

## Interface

- Layout de painel administrativo: navegação lateral fixa no desktop (a partir de 900 px) e barra superior com abas no celular; a página atual tem `aria-current="page"`.
- **Visão geral** (`/overview`, rota padrão): cartões de resumo e a agenda de hoje (08h–20h UTC, uma faixa por sala). Tudo é calculado no cliente em `src/agenda.ts`, a partir de `GET /api/rooms`, `GET /api/reservations/mine` e, para `ADMIN`, `GET /api/reservations`; para `MEMBER` a agenda mostra só as próprias reservas.
- Listas em tabela com cabeçalho fixo; abaixo de 640 px cada linha vira um cartão.
- Cores em variáveis CSS no `:root` de `src/styles.css`; o tema escuro segue `prefers-color-scheme`. O botão de tema grava `data-theme` no `<html>` e a escolha em `localStorage` (chave `theme`).
- Ações destrutivas usam `ConfirmButton`: o primeiro clique só mostra "Confirmar …" e "Voltar" (Esc também desiste).
- Formulários: texto de ajuda e mensagens de validação ligados aos campos por `aria-describedby`; o botão fica desabilitado com "Salvando…" enquanto a requisição está em andamento.
- Escala de espaçamento 4/8/12/16/24/32 px e 4 tamanhos de texto; controles com no mínimo 44 px de altura.

## Notas

- Datas de reserva são em UTC, com alinhamento de 15 minutos.
- Em 401 o cliente tenta `/api/auth/refresh` uma vez; se falhar, a sessão é encerrada.
