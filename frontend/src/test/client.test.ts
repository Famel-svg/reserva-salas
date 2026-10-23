import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, getTokens, logout, request, setAuthLostHandler, setTokens } from '../api/client';
import { jsonResponse, mockFetch } from './helpers';

beforeEach(() => setTokens({ accessToken: 'old', refreshToken: 'r1' }));
afterEach(() => vi.unstubAllGlobals());

describe('cliente HTTP', () => {
  it('renova o token em 401 e repete a chamada', async () => {
    const f = mockFetch((url, init) => {
      if (url === '/api/auth/refresh') return jsonResponse(200, { accessToken: 'new', refreshToken: 'r2' });
      const auth = new Headers(init?.headers).get('Authorization');
      return auth === 'Bearer new' ? jsonResponse(200, [{ id: '1' }]) : jsonResponse(401, { title: 'Não autenticado' });
    });

    expect(await request('/rooms')).toEqual([{ id: '1' }]);
    expect(getTokens()).toEqual({ accessToken: 'new', refreshToken: 'r2' });
    expect(f.mock.calls.map((c) => c[0])).toEqual(['/api/rooms', '/api/auth/refresh', '/api/rooms']);
  });

  it('desloga se a renovação falhar', async () => {
    const lost = vi.fn();
    setAuthLostHandler(lost);
    mockFetch(() => jsonResponse(401, { title: 'Não autenticado', detail: 'expirado' }));

    await expect(request('/rooms')).rejects.toBeInstanceOf(ApiError);
    expect(getTokens()).toBeNull();
    expect(lost).toHaveBeenCalledOnce();
  });

  it('logout chama /auth/logout com o refreshToken e limpa os tokens', async () => {
    const f = mockFetch(() => jsonResponse(204));
    await logout();
    expect(getTokens()).toBeNull();
    expect(f).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({ method: 'POST', body: '{"refreshToken":"r1"}' }));
  });

  it('expõe Retry-After em 429', async () => {
    mockFetch(() => jsonResponse(429, { title: 'Muitas requisições', detail: 'limite' }, { 'Retry-After': '30' }));
    await expect(request('/rooms')).rejects.toMatchObject({ status: 429, retryAfter: 30 });
  });
});
