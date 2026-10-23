import type { Reservation, Room } from './api/api';

/** Faixa visível da agenda do dia, em horas UTC. */
export const AGENDA_FROM = 8;
export const AGENDA_TO = 20;

const HOUR = 3_600_000;
const hhmm = (d: Date) => d.toISOString().slice(11, 16);

/** Início (00:00 UTC) do dia de `now`. */
export const utcDayStart = (now: Date) => Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());

/** Intervalo [início, fim) toca o dia UTC de `now`. */
export function touchesDay(r: Reservation, now: Date) {
  const day = utcDayStart(now);
  return Date.parse(r.startsAt) < day + 24 * HOUR && Date.parse(r.endsAt) > day;
}

const active = (list: Reservation[]) => list.filter((r) => r.status === 'ACTIVE');
const byStart = (a: Reservation, b: Reservation) => Date.parse(a.startsAt) - Date.parse(b.startsAt);

export interface Summary {
  rooms: number;
  seats: number;
  today: number;
  roomsUsedToday: number;
  upcoming: Reservation[];
  activeTotal: number | null;
  cancelledTotal: number | null;
}

/**
 * Números da visão geral. `all` só existe para ADMIN; sem ele, "hoje" conta só as reservas do próprio usuário.
 */
export function summarize(rooms: Room[], mine: Reservation[], all: Reservation[] | null, now: Date): Summary {
  const today = active(all ?? mine).filter((r) => touchesDay(r, now));
  return {
    rooms: rooms.length,
    seats: rooms.reduce((n, r) => n + r.capacity, 0),
    today: today.length,
    roomsUsedToday: new Set(today.map((r) => r.roomId)).size,
    upcoming: active(mine)
      .filter((r) => Date.parse(r.startsAt) > now.getTime())
      .sort(byStart),
    activeTotal: all && active(all).length,
    cancelledTotal: all && all.length - active(all).length,
  };
}

export interface Block {
  id: string;
  /** Posição e largura em % da faixa AGENDA_FROM–AGENDA_TO. */
  left: number;
  width: number;
  /** Horário real (sem corte), ex.: "09:00–10:30". */
  label: string;
  /** Começa antes ou termina depois da faixa visível. */
  cutStart: boolean;
  cutEnd: boolean;
  mine: boolean;
}

export interface AgendaRow {
  room: Room;
  blocks: Block[];
}

/**
 * Uma linha por sala com os blocos das reservas ativas do dia UTC de `now`, cortados nas bordas da faixa.
 * `hidden` conta as reservas do dia que ficam inteiras fora da faixa (madrugada ou noite).
 */
export function agenda(rooms: Room[], reservations: Reservation[], mineIds: Set<string>, now: Date) {
  const from = utcDayStart(now) + AGENDA_FROM * HOUR;
  const to = utcDayStart(now) + AGENDA_TO * HOUR;
  const span = to - from;
  let hidden = 0;
  const rows: AgendaRow[] = rooms.map((room) => ({ room, blocks: [] }));
  const rowOf = new Map(rows.map((row) => [row.room.id, row]));
  for (const r of active(reservations).filter((x) => touchesDay(x, now)).sort(byStart)) {
    const s = Date.parse(r.startsAt);
    const e = Date.parse(r.endsAt);
    const row = rowOf.get(r.roomId);
    if (!row) continue;
    if (e <= from || s >= to) {
      hidden++;
      continue;
    }
    const cs = Math.max(s, from);
    const ce = Math.min(e, to);
    row.blocks.push({
      id: r.id,
      left: ((cs - from) / span) * 100,
      width: ((ce - cs) / span) * 100,
      label: `${hhmm(new Date(s))}–${hhmm(new Date(e))}`,
      cutStart: s < from,
      cutEnd: e > to,
      mine: mineIds.has(r.id),
    });
  }
  return { rows, hidden };
}

/** Posição (%) da linha "agora" na faixa, ou null fora dela. */
export function nowMark(now: Date) {
  const h = (now.getTime() - utcDayStart(now)) / HOUR;
  return h < AGENDA_FROM || h > AGENDA_TO ? null : ((h - AGENDA_FROM) / (AGENDA_TO - AGENDA_FROM)) * 100;
}
