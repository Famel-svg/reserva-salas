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

- Cores em variáveis CSS no `:root` de `src/styles.css`; o tema escuro segue `prefers-color-scheme` e pode ser forçado com `data-theme="light"` ou `"dark"` no `<html>`.
- Ações destrutivas usam `ConfirmButton`: o primeiro clique só mostra "Confirmar …" e "Voltar" (Esc também desiste).
- Controles têm no mínimo 44 px de altura; abaixo de 860 px a tela vira uma coluna.

## Notas

- Datas de reserva são em UTC, com alinhamento de 15 minutos.
- Em 401 o cliente tenta `/api/auth/refresh` uma vez; se falhar, a sessão é encerrada.
