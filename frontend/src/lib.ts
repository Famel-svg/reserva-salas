import { ApiError } from './api/client';

/** Interpreta o valor de <input type="datetime-local"> como UTC. */
export const parseUtc = (v: string) => new Date(`${v}:00Z`);

/** Retorna mensagem em portugues ou null se valido. */
export function validateRange(start: string, end: string): string | null {
  if (!start || !end) return 'Informe início e fim.';
  const s = parseUtc(start);
  const e = parseUtc(end);
  if (isNaN(s.getTime()) || isNaN(e.getTime())) return 'Data inválida.';
  if (s.getUTCMinutes() % 15 !== 0 || e.getUTCMinutes() % 15 !== 0) {
    return 'Início e fim devem estar alinhados a 15 minutos (:00, :15, :30, :45).';
  }
  if (s >= e) return 'O início deve ser anterior ao fim.';
  return null;
}

export const fmt = (iso: string) => new Date(iso).toISOString().slice(0, 16).replace('T', ' ') + ' UTC';

export function errorParts(err: unknown): { title: string; detail: string } {
  if (err instanceof ApiError) {
    const wait = err.retryAfter ? ` Tente novamente em ${err.retryAfter} s.` : '';
    return { title: err.title, detail: err.detail + wait };
  }
  return { title: 'Erro de rede', detail: 'Não foi possível falar com o servidor.' };
}
