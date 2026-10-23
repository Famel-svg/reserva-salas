import { useCallback, useEffect, useState, type FormEvent } from 'react';
import * as api from '../api/api';
import { useAuth } from '../auth/AuthContext';
import ErrorBanner from '../components/ErrorBanner';

export default function RoomsPage() {
  const { role } = useAuth();
  const isAdmin = role === 'ADMIN';
  const [rooms, setRooms] = useState<api.Room[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [editing, setEditing] = useState<string | undefined>();
  const [name, setName] = useState('');
  const [capacity, setCapacity] = useState('1');

  const load = useCallback(async () => {
    try {
      setRooms(await api.listRooms());
    } catch (err) {
      setError(err);
      setRooms([]);
    }
  }, []);
  useEffect(() => void load(), [load]);

  async function run(fn: () => Promise<unknown>) {
    setError(null);
    try {
      await fn();
      await load();
    } catch (err) {
      setError(err);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    void run(async () => {
      await api.saveRoom({ name, capacity: Number(capacity) }, editing);
      setEditing(undefined);
      setName('');
      setCapacity('1');
    });
  }

  return (
    <section>
      <h1>Salas</h1>
      <ErrorBanner error={error} />
      {rooms === null && <p>Carregando…</p>}
      {rooms?.length === 0 && <p>Nenhuma sala cadastrada.</p>}
      <ul className="list">
        {rooms?.map((r) => (
          <li key={r.id}>
            <span>
              <strong>{r.name}</strong> — {r.capacity} lugares
            </span>
            {isAdmin && (
              <span className="actions">
                <button
                  onClick={() => {
                    setEditing(r.id);
                    setName(r.name);
                    setCapacity(String(r.capacity));
                  }}
                >
                  Editar
                </button>
                <button className="danger" onClick={() => void run(() => api.deleteRoom(r.id))}>
                  Remover
                </button>
              </span>
            )}
          </li>
        ))}
      </ul>
      {isAdmin && (
        <form className="card" onSubmit={submit}>
          <h2>{editing ? 'Editar sala' : 'Nova sala'}</h2>
          <label>
            Nome
            <input value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label>
            Capacidade
            <input type="number" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} required />
          </label>
          <button type="submit">{editing ? 'Salvar' : 'Criar sala'}</button>
          {editing && (
            <button type="button" onClick={() => { setEditing(undefined); setName(''); setCapacity('1'); }}>
              Cancelar edição
            </button>
          )}
        </form>
      )}
    </section>
  );
}
