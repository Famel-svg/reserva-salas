import * as api from '../api/api';
import { fmtRange } from '../lib';
import ConfirmButton from './ConfirmButton';
import EmptyState from './EmptyState';
import { CalendarIcon } from './Icons';

/** Tabela no desktop; abaixo de 640px cada linha vira um cartão (rótulos vêm de data-label). */
export default function ReservationTable({ items, rooms, onChanged, setError, empty, caption }: {
  items: api.Reservation[];
  rooms: api.Room[];
  onChanged: () => void;
  setError: (e: unknown) => void;
  empty: string;
  caption: string;
}) {
  const roomName = (id: string) => rooms.find((r) => r.id === id)?.name ?? id;
  async function run(fn: () => Promise<unknown>) {
    setError(null);
    try {
      await fn();
      onChanged();
    } catch (err) {
      setError(err);
    }
  }
  if (items.length === 0) {
    return (
      <EmptyState icon={<CalendarIcon />} title="Nenhuma reserva.">
        {empty}
      </EmptyState>
    );
  }
  const sorted = [...items].sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt));
  return (
    <div className="table-wrap">
      <table className="table">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            <th scope="col">Sala</th>
            <th scope="col">Quando</th>
            <th scope="col">Status</th>
            <th scope="col"><span className="sr-only">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((r) => (
            <tr key={r.id} className={r.status === 'CANCELLED' ? 'is-cancelled' : undefined}>
              <td data-label="Sala"><strong>{roomName(r.roomId)}</strong></td>
              <td data-label="Quando" className="num">{fmtRange(r.startsAt, r.endsAt)}</td>
              <td data-label="Status">
                <span className="chips">
                  {r.status === 'ACTIVE' ? <span className="chip active">Ativa</span> : <span className="chip">Cancelada</span>}
                  {r.seriesId && <span className="chip series">Série semanal</span>}
                </span>
              </td>
              <td className="cell-actions">
                {r.status === 'ACTIVE' && (
                  <span className="actions">
                    <ConfirmButton label="Cancelar" confirmLabel="Confirmar cancelamento" onConfirm={() => void run(() => api.cancelReservation(r.id))} />
                    {r.seriesId && (
                      <ConfirmButton label="Cancelar série" confirmLabel="Cancelar toda a série" onConfirm={() => void run(() => api.cancelSeries(r.seriesId!))} />
                    )}
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
