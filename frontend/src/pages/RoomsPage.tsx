import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import * as api from '../api/api';
import { useAuth } from '../auth/AuthContext';
import ConfirmButton from '../components/ConfirmButton';
import EmptyState from '../components/EmptyState';
import ErrorBanner from '../components/ErrorBanner';
import { PlusIcon, RoomIcon } from '../components/Icons';
import PageHeader from '../components/PageHeader';

export default function RoomsPage() {
  const { role } = useAuth();
  const isAdmin = role === 'ADMIN';
  const [rooms, setRooms] = useState<api.Room[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [editing, setEditing] = useState<string | undefined>();
  const [name, setName] = useState('');
  const [capacity, setCapacity] = useState('1');
  const [saving, setSaving] = useState(false);
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
    if (saving) return;
    setSaving(true);
    void run(async () => {
      await api.saveRoom({ name, capacity: Number(capacity) }, editing);
      setEditing(undefined);
      setName('');
      setCapacity('1');
    }).finally(() => setSaving(false));
  }

  const seats = rooms?.reduce((n, r) => n + r.capacity, 0) ?? 0;
  return (
    <section>
      <PageHeader
        title="Salas"
        subtitle={
          rooms && rooms.length > 0
            ? `${rooms.length} ${rooms.length === 1 ? 'sala' : 'salas'} · ${seats} ${seats === 1 ? 'lugar' : 'lugares'} no total`
            : 'Espaços disponíveis para reserva.'
        }
        action={
          isAdmin && (
            <button type="button" className="primary" onClick={() => { setEditing(undefined); setName(''); setCapacity('1'); nameRef.current?.focus(); }}>
              <PlusIcon />
              Nova sala
            </button>
          )
        }
      />
      <ErrorBanner error={error} />
      <div className={isAdmin ? 'split' : undefined}>
        <div className="card stack">
          <div className="card-head">
            <h2>Todas as salas</h2>
          </div>
          {rooms === null && <p className="loading" role="status">Carregando…</p>}
          {rooms?.length === 0 && (
            <EmptyState icon={<RoomIcon />} title="Nenhuma sala cadastrada.">
              {isAdmin ? 'Crie a primeira no formulário ao lado.' : 'Peça a um administrador para cadastrar salas.'}
            </EmptyState>
          )}
          {rooms && rooms.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <caption className="sr-only">Salas cadastradas</caption>
                <thead>
                  <tr>
                    <th scope="col">Nome</th>
                    <th scope="col" className="right">Capacidade</th>
                    {isAdmin && <th scope="col"><span className="sr-only">Ações</span></th>}
                  </tr>
                </thead>
                <tbody>
                  {rooms.map((r) => (
                    <tr key={r.id} className={editing === r.id ? 'is-editing' : undefined}>
                      <td data-label="Nome"><strong>{r.name}</strong></td>
                      <td data-label="Capacidade" className="right num">{r.capacity} {r.capacity === 1 ? 'lugar' : 'lugares'}</td>
                      {isAdmin && (
                        <td className="cell-actions">
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
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        {isAdmin && (
          <form className="card panel" onSubmit={submit}>
            <div className="form-title">
              <h2>{editing ? 'Editar sala' : 'Nova sala'}</h2>
              <p className="hint">{editing ? 'As reservas existentes continuam valendo.' : 'Fica disponível para reserva assim que for criada.'}</p>
            </div>
            <div className="field">
              <label htmlFor="room-name">Nome</label>
              <input id="room-name" aria-describedby="room-name-hint" ref={nameRef} value={name} onChange={(e) => setName(e.target.value)} required />
              <p className="hint" id="room-name-hint">Aparece na lista, nas reservas e na agenda.</p>
            </div>
            <div className="field">
              <label htmlFor="room-capacity">Capacidade</label>
              <input id="room-capacity" aria-describedby="room-capacity-hint" type="number" inputMode="numeric" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} required />
              <p className="hint" id="room-capacity-hint">Número de lugares, no mínimo 1.</p>
            </div>
            <div className="form-actions">
              <button type="submit" disabled={saving}>{saving ? 'Salvando…' : editing ? 'Salvar' : 'Criar sala'}</button>
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
