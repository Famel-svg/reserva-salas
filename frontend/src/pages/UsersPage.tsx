import { useState, type FormEvent } from 'react';
import * as api from '../api/api';
import ErrorBanner from '../components/ErrorBanner';
import { CheckIcon } from '../components/Icons';

export default function UsersPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<api.Role>('MEMBER');
  const [error, setError] = useState<unknown>(null);
  const [ok, setOk] = useState('');

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setOk('');
    try {
      const u = await api.createUser({ email, password, role });
      setOk(`Usuário ${u.email} criado como ${u.role}.`);
      setEmail('');
      setPassword('');
    } catch (err) {
      setError(err);
    }
  }

  return (
    <section>
      <div className="page-head">
        <h1>Usuários</h1>
      </div>
      <div className="card narrow stack">
        <h2>Novo usuário</h2>
        <ErrorBanner error={error} />
        {ok && (
          <div className="ok" role="status">
            <CheckIcon />
            <span>{ok}</span>
          </div>
        )}
        <form onSubmit={submit}>
          <label>
            E-mail
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <label>
            Senha (8 a 72 caracteres)
            <input type="password" minLength={8} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="new-password" />
          </label>
          <label>
            Papel
            <select value={role} onChange={(e) => setRole(e.target.value as api.Role)}>
              <option value="MEMBER">MEMBER (reserva salas)</option>
              <option value="ADMIN">ADMIN (gerencia salas e usuários)</option>
            </select>
          </label>
          <button type="submit">Criar usuário</button>
        </form>
      </div>
    </section>
  );
}
