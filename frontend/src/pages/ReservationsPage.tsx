import { useCallback, useEffect, useState, type FormEvent } from 'react';
import * as api from '../api/api';
import { useAuth } from '../auth/AuthContext';
import ConfirmButton from '../components/ConfirmButton';
import ErrorBanner from '../components/ErrorBanner';
import { AlertIcon } from '../components/Icons';
import { fmtRange, parseUtc, validateRange } from '../lib';

export function ReservationForm({ rooms, onDone }: { rooms: api.Room[]; onDone: () => void }) {
  const [roomId, setRoomId] = useState('');
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [recurring, setRecurring] = useState(false);
  const [occurrences, setOccurrences] = useState('2');
  const [error, setError] = useState<unknown>(null);
  const [validation, setValidation] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const msg = !(roomId || rooms[0]) ? 'Escolha uma sala.' : validateRange(start, end);
    setValidation(msg);
    if (msg) return;
    const body = {
      roomId: roomId || rooms[0].id,
      startsAt: parseUtc(start).toISOString(),
      endsAt: parseUtc(end).toISOString(),
    };
    try {
      if (recurring) await api.reserveRecurring({ ...body, occurrences: Number(occurrences) });
      else await api.reserve(body);
      onDone();
    } catch (err) {
      setError(err);
    }
  }

  return (
    <form className="card panel" onSubmit={submit} noValidate>
      <h2>Reservar sala</h2>
      <ErrorBanner error={error} />
      {validation && (
        <div className="error" role="alert">
          <AlertIcon />
          <span>{validation}</span>
        </div>
      )}
      <label>
        Sala
        <select value={roomId || rooms[0]?.id || ''} onChange={(e) => setRoomId(e.target.value)}>
          {rooms.map((r) => (
            <option key={r.id} value={r.id}>{r.name} ({r.capacity} lugares)</option>
          ))}
        </select>
      </label>
      <label>
        Início (UTC)
        <input type="datetime-local" step={900} value={start} onChange={(e) => setStart(e.target.value)} />
      </label>
      <label>
        Fim (UTC)
        <input type="datetime-local" step={900} value={end} onChange={(e) => setEnd(e.target.value)} />
      </label>
      <p className="hint">Horários em UTC, em múltiplos de 15 minutos (:00, :15, :30, :45).</p>
      <label className="check">
        <input type="checkbox" checked={recurring} onChange={(e) => setRecurring(e.target.checked)} />
        Repetir semanalmente
      </label>
      {recurring && (
        <label>
          Ocorrências
          <select value={occurrences} onChange={(e) => setOccurrences(e.target.value)}>
            <option value="2">2</option>
            <option value="3">3</option>
          </select>
        </label>
      )}
      <button type="submit">Reservar</button>
    </form>
  );
}

function ReservationList({ items, rooms, onChanged, setError, empty }: {
  items: api.Reservation[];
  rooms: api.Room[];
  onChanged: () => void;
  setError: (e: unknown) => void;
  empty: string;
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
      <div className="empty">
        <strong>Nenhuma reserva.</strong>
        <span>{empty}</span>
      </div>
    );
  }
  return (
    <ul className="list">
      {items.map((r) => (
        <li key={r.id} className={r.status === 'CANCELLED' ? 'row is-cancelled' : 'row'}>
          <span className="row-main">
            <strong className="row-title">{roomName(r.roomId)}</strong>
            <span className="meta">{fmtRange(r.startsAt, r.endsAt)}</span>
            <span className="chips">
              {r.status === 'ACTIVE' ? <span className="chip active">Ativa</span> : <span className="chip">Cancelada</span>}
              {r.seriesId && <span className="chip series">Série semanal</span>}
            </span>
          </span>
          {r.status === 'ACTIVE' && (
            <span className="actions">
              <ConfirmButton label="Cancelar" confirmLabel="Confirmar cancelamento" onConfirm={() => void run(() => api.cancelReservation(r.id))} />
              {r.seriesId && (
                <ConfirmButton label="Cancelar série" confirmLabel="Cancelar toda a série" onConfirm={() => void run(() => api.cancelSeries(r.seriesId!))} />
              )}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

const countLabel = (n: number) => `${n} ${n === 1 ? 'reserva' : 'reservas'}`;

export default function ReservationsPage() {
  const { role } = useAuth();
  const [rooms, setRooms] = useState<api.Room[] | null>(null);
  const [mine, setMine] = useState<api.Reservation[] | null>(null);
  const [all, setAll] = useState<api.Reservation[] | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(async () => {
    try {
      setRooms(await api.listRooms());
      setMine(await api.myReservations());
      if (role === 'ADMIN') setAll(await api.allReservations());
    } catch (err) {
      setError(err);
      setRooms((r) => r ?? []);
      setMine((m) => m ?? []);
    }
  }, [role]);
  useEffect(() => void load(), [load]);

  if (rooms === null || mine === null) return <p className="loading" role="status">Carregando…</p>;
  return (
    <section>
      <div className="page-head">
        <h1>Reservas</h1>
      </div>
      <ErrorBanner error={error} />
      <div className="split">
        {rooms.length > 0 ? (
          <ReservationForm rooms={rooms} onDone={() => void load()} />
        ) : (
          <div className="empty panel">
            <strong>Cadastre uma sala para reservar.</strong>
            <span>Um administrador cria salas na aba Salas.</span>
          </div>
        )}
        <div className="stack">
          <div className="section-head">
            <h2>Minhas reservas</h2>
            <span className="count">{countLabel(mine.length)}</span>
          </div>
          <ReservationList items={mine} rooms={rooms} onChanged={() => void load()} setError={setError} empty="Use o formulário para reservar uma sala." />
          {role === 'ADMIN' && all && (
            <>
              <div className="section-head stack-gap">
                <h2>Todas as reservas</h2>
                <span className="count">{countLabel(all.length)}</span>
              </div>
              <ReservationList items={all} rooms={rooms} onChanged={() => void load()} setError={setError} empty="Ninguém reservou salas ainda." />
            </>
          )}
        </div>
      </div>
    </section>
  );
}
