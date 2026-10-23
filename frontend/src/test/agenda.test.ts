import { describe, expect, it } from 'vitest';
import type { Reservation } from '../api/api';
import { agenda, nowMark, summarize, touchesDay } from '../agenda';

const now = new Date('2030-01-10T12:00:00Z');
const rooms = [
  { id: 'a', name: 'Sala A', capacity: 4 },
  { id: 'b', name: 'Sala B', capacity: 10 },
];
let seq = 0;
const res = (roomId: string, startsAt: string, endsAt: string, status: Reservation['status'] = 'ACTIVE'): Reservation => ({
  id: `r${++seq}`,
  roomId,
  userId: 'u',
  startsAt,
  endsAt,
  status,
  seriesId: null,
});

describe('agenda do dia', () => {
  it('posiciona 09:00–10:30 proporcionalmente na faixa 08–20', () => {
    const r = res('a', '2030-01-10T09:00:00Z', '2030-01-10T10:30:00Z');
    const [row] = agenda(rooms, [r], new Set([r.id]), now).rows;
    expect(row.blocks).toHaveLength(1);
    expect(row.blocks[0].left).toBeCloseTo(100 / 12);
    expect(row.blocks[0].width).toBeCloseTo(12.5);
    expect(row.blocks[0]).toMatchObject({ label: '09:00–10:30', cutStart: false, cutEnd: false, mine: true });
  });

  it('ignora reserva cancelada', () => {
    const { rows } = agenda(rooms, [res('a', '2030-01-10T09:00:00Z', '2030-01-10T10:00:00Z', 'CANCELLED')], new Set(), now);
    expect(rows[0].blocks).toHaveLength(0);
  });

  it('corta na borda reserva que começa antes das 08h e mantém o horário real no rótulo', () => {
    const { rows } = agenda(rooms, [res('b', '2030-01-10T07:00:00Z', '2030-01-10T09:00:00Z')], new Set(), now);
    const b = rows[1].blocks[0];
    expect(b.left).toBe(0);
    expect(b.width).toBeCloseTo(100 / 12);
    expect(b).toMatchObject({ label: '07:00–09:00', cutStart: true, mine: false });
  });

  it('conta como oculta a reserva do dia fora da faixa e ignora outros dias', () => {
    const { rows, hidden } = agenda(
      rooms,
      [res('a', '2030-01-10T05:00:00Z', '2030-01-10T06:00:00Z'), res('a', '2030-01-11T09:00:00Z', '2030-01-11T10:00:00Z')],
      new Set(),
      now,
    );
    expect(hidden).toBe(1);
    expect(rows[0].blocks).toHaveLength(0);
  });

  it('linha "agora" só aparece dentro da faixa', () => {
    expect(nowMark(now)).toBeCloseTo(100 / 3);
    expect(nowMark(new Date('2030-01-10T22:00:00Z'))).toBeNull();
  });
});

describe('resumo', () => {
  it('conta salas, reservas que tocam o dia UTC e próximas do usuário', () => {
    const overnight = res('a', '2030-01-09T23:00:00Z', '2030-01-10T01:00:00Z');
    const later = res('b', '2030-01-10T15:00:00Z', '2030-01-10T16:00:00Z');
    const tomorrow = res('a', '2030-01-11T09:00:00Z', '2030-01-11T10:00:00Z');
    const cancelled = res('b', '2030-01-10T17:00:00Z', '2030-01-10T18:00:00Z', 'CANCELLED');
    const mine = [tomorrow, later, cancelled];
    const all = [overnight, ...mine];

    expect(touchesDay(overnight, now)).toBe(true);
    const s = summarize(rooms, mine, all, now);
    expect(s).toMatchObject({ rooms: 2, seats: 14, today: 2, roomsUsedToday: 2, activeTotal: 3, cancelledTotal: 1 });
    expect(s.upcoming.map((r) => r.id)).toEqual([later.id, tomorrow.id]);

    const member = summarize(rooms, mine, null, now);
    expect(member).toMatchObject({ today: 1, activeTotal: null });
  });
});
