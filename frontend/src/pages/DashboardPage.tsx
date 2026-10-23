import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../api/api';
import { useAuth } from '../auth/AuthContext';
import { summarize } from '../agenda';
import Agenda from '../components/Agenda';
import ErrorBanner from '../components/ErrorBanner';
import PageHeader from '../components/PageHeader';
import ReservationTable from '../components/ReservationTable';
import { CalendarIcon, CheckIcon, ClockIcon, PlusIcon, RoomIcon } from '../components/Icons';
import { fmtShort } from '../lib';

const today = (now: Date) =>
  new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(now);

function Stat({ icon, label, value, sub }: { icon: ReactNode; label: string; value: number; sub: ReactNode }) {
  return (
    <div className="stat">
      <span className="stat-label">{icon}{label}</span>
      <strong className="stat-value">{value}</strong>
      <span className="stat-sub">{sub}</span>
    </div>
  );
}

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

export default function DashboardPage() {
  const { role } = useAuth();
  const isAdmin = role === 'ADMIN';
  const [data, setData] = useState<{ rooms: api.Room[]; mine: api.Reservation[]; all: api.Reservation[] | null } | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [now] = useState(() => new Date());

  const load = useCallback(async () => {
    try {
      const [rooms, mine, all] = await Promise.all([api.listRooms(), api.myReservations(), isAdmin ? api.allReservations() : null]);
      setData({ rooms, mine, all });
    } catch (err) {
      setError(err);
      setData((d) => d ?? { rooms: [], mine: [], all: null });
    }
  }, [isAdmin]);
  useEffect(() => void load(), [load]);

  const header = (
    <PageHeader
      title="Visão geral"
      subtitle={<>{today(now)} · horários em UTC</>}
      action={
        <Link className="button primary" to="/reservations#reservar">
          <PlusIcon />
          Nova reserva
        </Link>
      }
    />
  );
  if (!data) return <section>{header}<p className="loading" role="status">Carregando…</p></section>;

  const s = summarize(data.rooms, data.mine, data.all, now);
  const next = s.upcoming[0];
  const roomName = (id: string) => data.rooms.find((r) => r.id === id)?.name ?? id;

  return (
    <section>
      {header}
      <ErrorBanner error={error} />
      <div className="stats">
        <Stat icon={<RoomIcon />} label="Salas" value={s.rooms} sub={plural(s.seats, 'lugar no total', 'lugares no total')} />
        <Stat
          icon={<CalendarIcon />}
          label="Reservas hoje"
          value={s.today}
          sub={`em ${s.roomsUsedToday} de ${s.rooms} ${s.rooms === 1 ? 'sala' : 'salas'}${isAdmin ? '' : ' (só as suas)'}`}
        />
        <Stat
          icon={<ClockIcon />}
          label="Minhas próximas"
          value={s.upcoming.length}
          sub={next ? <>Próxima: {roomName(next.roomId)}, <span className="num">{fmtShort(next.startsAt)}</span></> : 'Nenhuma agendada'}
        />
        {s.activeTotal !== null && (
          <Stat icon={<CheckIcon />} label="Reservas ativas no total" value={s.activeTotal} sub={plural(s.cancelledTotal ?? 0, 'cancelada', 'canceladas')} />
        )}
      </div>
      <Agenda rooms={data.rooms} reservations={data.all ?? data.mine} mineIds={new Set(data.mine.map((r) => r.id))} now={now} isAdmin={isAdmin} />
      <div className="card stack">
        <div className="card-head">
          <h2>Minhas próximas reservas</h2>
          <Link to="/reservations">Ver todas</Link>
        </div>
        <ReservationTable
          items={s.upcoming.slice(0, 5)}
          rooms={data.rooms}
          onChanged={() => void load()}
          setError={setError}
          empty="Use “Nova reserva” para agendar uma sala."
          caption="Minhas próximas reservas"
        />
      </div>
    </section>
  );
}
