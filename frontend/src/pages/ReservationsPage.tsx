import { useCallback, useEffect, useState, type FormEvent } from 'react';
import * as api from '../api/api';
import { useAuth } from '../auth/AuthContext';
import ErrorBanner from '../components/ErrorBanner';
import { fmt, parseUtc, validateRange } from '../lib';

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
    <form className="card" onSubmit={submit} noValidate>
      <h2>Reservar sala</h2>
      <ErrorBanner error={error} />
      {validation && <div className="error" role="alert">{validation}</div>}
      <label>
        Sala
        <select value={roomId || rooms[0]?.id || ''} onChange={(e) => setRoomId(e.target.value)}>
          {rooms.map((r) => (
            <option key={r.id} value={r.id}>{r.name}</option>
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
      <label className="inline">
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

function ReservationList({ items, rooms, onChanged, setError }: {
  items: api.Reservation[];
  rooms: api.Room[];
  onChanged: () => void;
  setError: (e: unknown) => void;
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
  if (items.length === 0) return <p>Nenhuma reserva.</p>;
  return (
    <ul className="list">
      {items.map((r) => (
        <li key={r.id}>
          <span>
            <strong>{roomName(r.roomId)}</strong> — {fmt(r.startsAt)} a {fmt(r.endsAt)}
            {r.status === 'CANCELLED' && ' (cancelada)'}
            {r.seriesId && ' · série'}
          </span>
          {r.status === 'ACTIVE' && (
            <span className="actions">
              <button className="danger" onClick={() => void run(() => api.cancelReservation(r.id))}>Cancelar</button>
              {r.seriesId && (
                <button className="danger" onClick={() => void run(() => api.cancelSeries(r.seriesId!))}>Cancelar série</button>
              )}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

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

  if (rooms === null || mine === null) return <p>Carregando…</p>;
  return (
    <section>
      <h1>Reservas</h1>
      <ErrorBanner error={error} />
      {rooms.length > 0 ? <ReservationForm rooms={rooms} onDone={() => void load()} /> : <p>Cadastre uma sala para reservar.</p>}
      <h2>Minhas reservas</h2>
      <ReservationList items={mine} rooms={rooms} onChanged={() => void load()} setError={setError} />
      {role === 'ADMIN' && all && (
        <>
          <h2>Todas as reservas</h2>
          <ReservationList items={all} rooms={rooms} onChanged={() => void load()} setError={setError} />
        </>
      )}
    </section>
  );
}
