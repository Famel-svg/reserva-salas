import type { Reservation, Room } from '../api/api';
import { AGENDA_FROM, AGENDA_TO, agenda, nowMark } from '../agenda';
import EmptyState from './EmptyState';
import { RoomIcon } from './Icons';

const ticks = Array.from({ length: (AGENDA_TO - AGENDA_FROM) / 2 + 1 }, (_, i) => AGENDA_FROM + i * 2);
const pct = (h: number) => `${((h - AGENDA_FROM) / (AGENDA_TO - AGENDA_FROM)) * 100}%`;

/** Faixa 08h–20h UTC por sala, com blocos proporcionais; o intervalo exato aparece no hover e no foco. */
export default function Agenda({ rooms, reservations, mineIds, now, isAdmin }: {
  rooms: Room[];
  reservations: Reservation[];
  mineIds: Set<string>;
  now: Date;
  isAdmin: boolean;
}) {
  const { rows, hidden } = agenda(rooms, reservations, mineIds, now);
  const mark = nowMark(now);
  return (
    <div className="card agenda-card">
      <div className="card-head">
        <h2>Agenda de hoje</h2>
        <span className="count">{isAdmin ? 'Todas as reservas' : 'Só as suas reservas'} · 08h–20h UTC</span>
      </div>
      {rooms.length === 0 ? (
        <EmptyState icon={<RoomIcon />} title="Nenhuma sala cadastrada.">
          {isAdmin ? 'Crie a primeira na aba Salas.' : 'Peça a um administrador para cadastrar salas.'}
        </EmptyState>
      ) : (
        <>
          <div className="agenda">
            <div className="agenda-axis" aria-hidden="true">
              {ticks.map((h) => (
                <span key={h} style={{ left: pct(h) }}>{String(h).padStart(2, '0')}h</span>
              ))}
            </div>
            <ul className="agenda-rows">
              {rows.map(({ room, blocks }) => (
                <li key={room.id} className="agenda-row">
                  <span className="agenda-room">
                    <strong>{room.name}</strong>
                    <span className="num">{room.capacity} {room.capacity === 1 ? 'lugar' : 'lugares'}</span>
                  </span>
                  <div className="track">
                    {blocks.length === 0 && <span className="track-free">Livre</span>}
                    {mark !== null && <span className="now-line" style={{ left: `${mark}%` }} aria-hidden="true" />}
                    {blocks.map((b) => {
                      const [start, end] = b.label.split('–');
                      const cut = b.cutStart || b.cutEnd ? ' (passa da faixa visível)' : '';
                      return (
                        <span
                          key={b.id}
                          className={`block${b.mine ? ' is-mine' : ''}${b.left > 60 ? ' tip-left' : ''}`}
                          style={{ left: `${b.left}%`, width: `${b.width}%` }}
                          tabIndex={0}
                          aria-label={`${room.name}, ${b.label} UTC${b.mine ? ', sua reserva' : ''}${cut}`}
                        >
                          <span className="block-label num" aria-hidden="true">
                            {start}<span className="block-end">–{end}</span>
                          </span>
                          <span className="tip num" aria-hidden="true">{b.label} UTC{cut}</span>
                        </span>
                      );
                    })}
                  </div>
                </li>
              ))}
            </ul>
          </div>
          <div className="legend">
            <span><i className="swatch is-mine" />Minha reserva</span>
            {isAdmin && <span><i className="swatch" />Outras reservas</span>}
            {mark !== null && <span><i className="swatch now" />Agora</span>}
            {hidden > 0 && <span>+{hidden} fora da faixa 08h–20h</span>}
          </div>
        </>
      )}
    </div>
  );
}
