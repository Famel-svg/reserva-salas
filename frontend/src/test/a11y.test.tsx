import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReservationForm } from '../pages/ReservationsPage';
import ThemeToggle from '../components/ThemeToggle';
import { mockFetch } from './helpers';

// Node 26 expõe um localStorage próprio (indefinido sem --localstorage-file) que encobre o do jsdom.
const memoryStorage = () => {
  const m = new Map<string, string>();
  return { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v), removeItem: (k: string) => void m.delete(k), clear: () => m.clear() };
};
beforeEach(() => vi.stubGlobal('localStorage', memoryStorage()));
afterEach(() => {
  vi.unstubAllGlobals();
  delete document.documentElement.dataset.theme;
});

const rooms = [{ id: 'r', name: 'Sala A', capacity: 4 }];

describe('formulário de reserva', () => {
  it('liga a mensagem de validação aos campos com aria-describedby', async () => {
    render(<ReservationForm rooms={rooms} onDone={() => {}} />);
    await userEvent.type(screen.getByLabelText('Início (UTC)'), '2030-01-01T11:00');
    await userEvent.type(screen.getByLabelText('Fim (UTC)'), '2030-01-01T10:00');
    await userEvent.click(screen.getByRole('button', { name: 'Reservar' }));
    const start = screen.getByLabelText('Início (UTC)');
    expect(start.getAttribute('aria-invalid')).toBe('true');
    const ids = start.getAttribute('aria-describedby')!.split(' ');
    expect(ids.map((id) => document.getElementById(id)?.textContent).join(' ')).toMatch(/anterior ao fim/);
  });

  it('bloqueia duplo envio enquanto a requisição está em andamento', async () => {
    const f = mockFetch(() => new Promise<Response>(() => {}));
    render(<ReservationForm rooms={rooms} onDone={() => {}} />);
    await userEvent.type(screen.getByLabelText('Início (UTC)'), '2030-01-01T10:00');
    await userEvent.type(screen.getByLabelText('Fim (UTC)'), '2030-01-01T11:00');
    await userEvent.click(screen.getByRole('button', { name: 'Reservar' }));
    const busy = await screen.findByRole('button', { name: 'Salvando…' });
    expect((busy as HTMLButtonElement).disabled).toBe(true);
    await userEvent.click(busy);
    expect(f).toHaveBeenCalledTimes(1);
  });
});

describe('tema', () => {
  it('alterna data-theme no <html> e guarda a escolha', async () => {
    render(<ThemeToggle />);
    const btn = screen.getByRole('button', { name: 'Tema escuro' });
    expect(btn.getAttribute('aria-pressed')).toBe('false');
    await userEvent.click(btn);
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem('theme')).toBe('dark');
    await userEvent.click(btn);
    expect(document.documentElement.dataset.theme).toBe('light');
  });
});
