import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import * as api from '../api/api';
import { useAuth } from '../auth/AuthContext';
import ConfirmButton from '../components/ConfirmButton';
import ErrorBanner from '../components/ErrorBanner';

export default function RoomsPage() {
  const { role } = useAuth();
  const isAdmin = role === 'ADMIN';
  const [rooms, setRooms] = useState<api.Room[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [editing, setEditing] = useState<string | undefined>();
  const [name, setName] = useState('');
  const [capacity, setCapacity] = useState('1');
  const nameRef = useRef<HTMLInputElement>(null);

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
      <div className="page-head">
        <h1>Salas</h1>
        {rooms && rooms.length > 0 && <p className="count">{rooms.length} {rooms.length === 1 ? 'sala' : 'salas'}</p>}
      </div>
      <ErrorBanner error={error} />
      <div className={isAdmin ? 'split' : undefined}>
        <div className="stack">
          {rooms === null && <p className="loading" role="status">Carregando…</p>}
          {rooms?.length === 0 && (
            <div className="empty">
              <strong>Nenhuma sala cadastrada.</strong>
              <span>{isAdmin ? 'Crie a primeira no formulário ao lado.' : 'Peça a um administrador para cadastrar salas.'}</span>
            </div>
          )}
          <ul className="list">
            {rooms?.map((r) => (
              <li key={r.id} className={editing === r.id ? 'row is-editing' : 'row'}>
                <span className="row-main">
                  <strong className="row-title">{r.name}</strong>
                  <span className="meta">{r.capacity} {r.capacity === 1 ? 'lugar' : 'lugares'}</span>
                </span>
                {isAdmin && (
                  <span className="actions">
                    <button
                      onClick={() => {
                        setEditing(r.id);
                        setName(r.name);
                        setCapacity(String(r.capacity));
                        nameRef.current?.focus();
                      }}
                    >
                      Editar
                    </button>
                    <ConfirmButton label="Remover" confirmLabel="Confirmar remoção" onConfirm={() => void run(() => api.deleteRoom(r.id))} />
                  </span>
                )}
              </li>
            ))}
          </ul>
        </div>
        {isAdmin && (
          <form className="card panel" onSubmit={submit}>
            <h2>{editing ? 'Editar sala' : 'Nova sala'}</h2>
            <label>
              Nome
              <input ref={nameRef} value={name} onChange={(e) => setName(e.target.value)} required />
            </label>
            <label>
              Capacidade
              <input type="number" inputMode="numeric" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} required />
            </label>
            <div className="form-actions">
              <button type="submit">{editing ? 'Salvar' : 'Criar sala'}</button>
              {editing && (
                <button type="button" onClick={() => { setEditing(undefined); setName(''); setCapacity('1'); }}>
                  Cancelar edição
                </button>
              )}
            </div>
          </form>
        )}
      </div>
    </section>
  );
}
