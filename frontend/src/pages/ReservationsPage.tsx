import { useCallback, useEffect, useState, type FormEvent } from 'react';
import * as api from '../api/api';
import { useAuth } from '../auth/AuthContext';
import EmptyState from '../components/EmptyState';
import ErrorBanner from '../components/ErrorBanner';
import { AlertIcon, PlusIcon, RoomIcon } from '../components/Icons';
import PageHeader from '../components/PageHeader';
import ReservationTable from '../components/ReservationTable';
import { parseUtc, validateRange } from '../lib';

export function ReservationForm({ rooms, onDone }: { rooms: api.Room[]; onDone: () => void }) {
  const [roomId, setRoomId] = useState('');
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [recurring, setRecurring] = useState(false);
  const [occurrences, setOccurrences] = useState('2');
  const [error, setError] = useState<unknown>(null);
  const [validation, setValidation] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Liga a mensagem de validação aos campos que ela descreve.
  const describe = (hint: string, invalid: boolean) => (invalid ? `${hint} res-validation` : hint);
  const roomInvalid = validation === 'Escolha uma sala.';
  const rangeInvalid = !!validation && !roomInvalid;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setError(null);
    const msg = !(roomId || rooms[0]) ? 'Escolha uma sala.' : validateRange(start, end);
    setValidation(msg);
    if (msg) return;
    const body = {
      roomId: roomId || rooms[0].id,
      startsAt: parseUtc(start).toISOString(),
      endsAt: parseUtc(end).toISOString(),
    };
    setBusy(true);
    try {
      if (recurring) await api.reserveRecurring({ ...body, occurrences: Number(occurrences) });
      else await api.reserve(body);
      onDone();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form id="reservar" className="card" onSubmit={submit} noValidate>
      <div className="form-title">
        <h2>Nova reserva</h2>
        <p className="hint">Horários em UTC, em múltiplos de 15 minutos (:00, :15, :30, :45).</p>
      </div>
      <ErrorBanner error={error} />
      {validation && (
        <div className="error" role="alert" id="res-validation">
          <AlertIcon />
          <span>{validation}</span>
        </div>
      )}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="res-room">Sala</label>
          <select id="res-room" aria-describedby={describe('res-room-hint', roomInvalid)} aria-invalid={roomInvalid || undefined} value={roomId || rooms[0]?.id || ''} onChange={(e) => setRoomId(e.target.value)}>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>{r.name} ({r.capacity} lugares)</option>
            ))}
          </select>
          <p className="hint" id="res-room-hint">A capacidade aparece entre parênteses.</p>
        </div>
        <div className="field">
          <label htmlFor="res-start">Início (UTC)</label>
          <input id="res-start" aria-describedby={describe('res-start-hint', rangeInvalid)} aria-invalid={rangeInvalid || undefined} type="datetime-local" step={900} value={start} onChange={(e) => setStart(e.target.value)} />
          <p className="hint" id="res-start-hint">Ex.: 09:00 ou 09:15.</p>
        </div>
        <div className="field">
          <label htmlFor="res-end">Fim (UTC)</label>
          <input id="res-end" aria-describedby={describe('res-end-hint', rangeInvalid)} aria-invalid={rangeInvalid || undefined} type="datetime-local" step={900} value={end} onChange={(e) => setEnd(e.target.value)} />
          <p className="hint" id="res-end-hint">Deve ser depois do início.</p>
        </div>
      </div>
      <div className="form-foot">
        <div className="options">
          <label className="check">
            <input type="checkbox" checked={recurring} onChange={(e) => setRecurring(e.target.checked)} />
            Repetir semanalmente
          </label>
          {recurring && (
            <div className="field">
              <label htmlFor="res-occ">Ocorrências</label>
              <select id="res-occ" value={occurrences} onChange={(e) => setOccurrences(e.target.value)} aria-describedby="res-occ-hint">
                <option value="2">2</option>
                <option value="3">3</option>
              </select>
            </div>
          )}
        </div>
        <button type="submit" disabled={busy}>{busy ? 'Salvando…' : 'Reservar'}</button>
      </div>
      {recurring && (
        <p className="hint" id="res-occ-hint">Mesmo dia e horário nas semanas seguintes; se uma ocorrência conflitar, nenhuma é criada.</p>
      )}
    </form>
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

  const ready = rooms !== null && mine !== null;
  // Vindo de "Nova reserva" na visão geral: foca o formulário quando ele existir.
  useEffect(() => {
    if (ready && window.location.hash === '#reservar') document.getElementById('res-start')?.focus();
  }, [ready]);

  const header = (
    <PageHeader
      title="Reservas"
      subtitle="Suas reservas e, para administradores, as de todos. Horários em UTC."
      action={
        rooms && rooms.length > 0 ? (
          <button type="button" className="primary" onClick={() => document.getElementById('res-start')?.focus()}>
            <PlusIcon />
            Nova reserva
          </button>
        ) : undefined
      }
    />
  );
  if (!ready) return <section>{header}<p className="loading" role="status">Carregando…</p></section>;
  return (
    <section>
      {header}
      <ErrorBanner error={error} />
      {rooms.length > 0 ? (
        <ReservationForm rooms={rooms} onDone={() => void load()} />
      ) : (
        <EmptyState icon={<RoomIcon />} title="Cadastre uma sala para reservar.">
          Um administrador cria salas na aba Salas.
        </EmptyState>
      )}
      <div className="card stack">
        <div className="card-head">
          <h2>Minhas reservas</h2>
          <span className="count">{countLabel(mine.length)}</span>
        </div>
        <ReservationTable items={mine} rooms={rooms} onChanged={() => void load()} setError={setError} empty="Use o formulário acima para reservar uma sala." caption="Minhas reservas" />
      </div>
      {role === 'ADMIN' && all && (
        <div className="card stack">
          <div className="card-head">
            <h2>Todas as reservas</h2>
            <span className="count">{countLabel(all.length)}</span>
          </div>
          <ReservationTable items={all} rooms={rooms} onChanged={() => void load()} setError={setError} empty="Ninguém reservou salas ainda." caption="Todas as reservas" />
        </div>
      )}
    </section>
  );
}
