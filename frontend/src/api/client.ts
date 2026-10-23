export interface Tokens {
  accessToken: string;
  refreshToken: string;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public title: string,
    public detail: string,
    public retryAfter?: number,
  ) {
    super(detail || title);
  }
}

const KEY = 'tokens';
let tokens: Tokens | null = null;
let onAuthLost: () => void = () => {};

try {
  tokens = JSON.parse(sessionStorage.getItem(KEY) ?? 'null');
} catch {
  tokens = null;
}

export const getTokens = () => tokens;
export const setAuthLostHandler = (fn: () => void) => {
  onAuthLost = fn;
};

export function setTokens(t: Tokens | null) {
  tokens = t;
  try {
    if (t) sessionStorage.setItem(KEY, JSON.stringify(t));
    else sessionStorage.removeItem(KEY);
  } catch {
    /* sessionStorage indisponivel: segue so em memoria */
  }
}

async function toError(res: Response): Promise<ApiError> {
  let body: { title?: string; detail?: string } = {};
  try {
    body = await res.json();
  } catch {
    /* corpo nao e JSON */
  }
  const ra = Number(res.headers.get('Retry-After'));
  return new ApiError(
    res.status,
    body.title ?? `Erro ${res.status}`,
    body.detail ?? '',
    res.status === 429 && ra > 0 ? ra : undefined,
  );
}

function send(path: string, init: RequestInit, withAuth: boolean) {
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  if (withAuth && tokens) headers.set('Authorization', `Bearer ${tokens.accessToken}`);
  return fetch(`/api${path}`, { ...init, headers });
}

let refreshing: Promise<boolean> | null = null;

// Uma unica renovacao em andamento, mesmo com varias chamadas recebendo 401 juntas.
function refresh(): Promise<boolean> {
  refreshing ??= (async () => {
    if (!tokens) return false;
    try {
      const res = await send(
        '/auth/refresh',
        { method: 'POST', body: JSON.stringify({ refreshToken: tokens.refreshToken }) },
        false,
      );
      if (!res.ok) return false;
      const t = await res.json();
      setTokens({ accessToken: t.accessToken, refreshToken: t.refreshToken });
      return true;
    } catch {
      return false;
    } finally {
      setTimeout(() => (refreshing = null), 0);
    }
  })();
  return refreshing;
}

export async function request<T>(path: string, init: RequestInit = {}, withAuth = true): Promise<T> {
  let res = await send(path, init, withAuth);
  if (res.status === 401 && withAuth) {
    if (await refresh()) res = await send(path, init, withAuth);
    if (res.status === 401) {
      setTokens(null);
      onAuthLost();
    }
  }
  if (!res.ok) throw await toError(res);
  return res.status === 204 ? (undefined as T) : res.json();
}

export const json = (method: string, body?: unknown): RequestInit => ({
  method,
  body: body === undefined ? undefined : JSON.stringify(body),
});

export async function logout() {
  const t = tokens;
  setTokens(null);
  if (t) {
    try {
      await send('/auth/logout', json('POST', { refreshToken: t.refreshToken }), false);
    } catch {
      /* sessao local ja encerrada */
    }
  }
}
