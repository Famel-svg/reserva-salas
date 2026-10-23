import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { AuthContext } from '../auth/AuthContext';
import type { Role } from '../api/api';
import LoginPage from '../pages/LoginPage';
import RoomsPage from '../pages/RoomsPage';
import { ReservationForm } from '../pages/ReservationsPage';
import ErrorBanner from '../components/ErrorBanner';
import { ApiError } from '../api/client';
import { fmtRange, validateRange } from '../lib';
import { jsonResponse, mockFetch } from './helpers';

afterEach(() => vi.unstubAllGlobals());

const withRole = (role: Role | null, ui: ReactNode, signIn = async () => {}) =>
  render(<AuthContext.Provider value={{ role, signIn, signOut: async () => {} }}>{ui}</AuthContext.Provider>);

describe('login', () => {
  it('mostra erro para credencial inválida', async () => {
    mockFetch(() => jsonResponse(401, { title: 'Não autenticado', detail: 'Credenciais inválidas' }));
    const { AuthProvider } = await import('../auth/AuthContext');
    render(
      <AuthProvider>
        <LoginPage />
      </AuthProvider>,
    );
    await userEvent.type(screen.getByLabelText('E-mail'), 'a@b.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'errada123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toContain('Não autenticado');
    expect(alert.textContent).toContain('Credenciais inválidas');
  });
});

describe('validação de reserva', () => {
  it('valida 15 minutos e início < fim', () => {
    expect(validateRange('2030-01-01T10:00', '2030-01-01T11:00')).toBeNull();
    expect(validateRange('2030-01-01T10:10', '2030-01-01T11:00')).toMatch(/15 minutos/);
    expect(validateRange('2030-01-01T11:00', '2030-01-01T10:00')).toMatch(/anterior ao fim/);
    expect(validateRange('2030-01-01T10:00', '2030-01-01T10:00')).toMatch(/anterior ao fim/);
  });

  it('formulário exibe a mensagem e não chama a API', async () => {
    const f = mockFetch(() => jsonResponse(201, {}));
    render(<ReservationForm rooms={[{ id: 'r', name: 'Sala A', capacity: 4 }]} onDone={() => {}} />);
    await userEvent.type(screen.getByLabelText('Início (UTC)'), '2030-01-01T10:07');
    await userEvent.type(screen.getByLabelText('Fim (UTC)'), '2030-01-01T11:00');
    await userEvent.click(screen.getByRole('button', { name: 'Reservar' }));
    expect((await screen.findByRole('alert')).textContent).toMatch(/15 minutos/);
    expect(f).not.toHaveBeenCalled();
  });
});

describe('salas', () => {
  const rooms = [{ id: '1', name: 'Sala A', capacity: 4 }];

  it('botões de ADMIN aparecem só para ADMIN', async () => {
    mockFetch(() => jsonResponse(200, rooms));
    withRole('ADMIN', <RoomsPage />);
    await screen.findByText('Sala A');
    expect(screen.getByRole('button', { name: 'Editar' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Remover' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Criar sala' })).toBeTruthy();
  });

  it('MEMBER não vê botões de administração', async () => {
    mockFetch(() => jsonResponse(200, rooms));
    withRole('MEMBER', <RoomsPage />);
    await waitFor(() => expect(screen.getByText('Sala A')).toBeTruthy());
    expect(screen.queryByRole('button', { name: 'Editar' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Remover' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Criar sala' })).toBeNull();
  });

  it('Remover pede confirmação antes de chamar DELETE', async () => {
    const f = mockFetch(() => jsonResponse(200, rooms));
    withRole('ADMIN', <RoomsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Remover' }));
    const deletes = () => f.mock.calls.filter(([, init]) => init?.method === 'DELETE');
    expect(deletes()).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Voltar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Remover' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar remoção' }));
    await waitFor(() => expect(deletes()).toHaveLength(1));
  });
});

describe('formatação', () => {
  it('encurta intervalo no mesmo dia', () => {
    expect(fmtRange('2030-01-01T10:00:00Z', '2030-01-01T11:30:00Z')).toBe('2030-01-01 10:00–11:30 UTC');
    expect(fmtRange('2030-01-01T23:00:00Z', '2030-01-02T01:00:00Z')).toBe('2030-01-01 23:00 UTC a 2030-01-02 01:00 UTC');
  });
});

describe('erro ProblemDetail', () => {
  it('renderiza title e detail', () => {
    render(<ErrorBanner error={new ApiError(409, 'Conflito', 'Conflito nas ocorrências: 2030-01-08T10:00:00Z')} />);
    const alert = screen.getByRole('alert');
    expect(alert.textContent).toContain('Conflito');
    expect(alert.textContent).toContain('2030-01-08T10:00:00Z');
  });

  it('mostra Retry-After em 429', () => {
    render(<ErrorBanner error={new ApiError(429, 'Muitas requisições', 'Limite excedido.', 30)} />);
    expect(screen.getByRole('alert').textContent).toContain('30 s');
  });
});
