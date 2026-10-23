import { useState, type FormEvent } from 'react';
import * as api from '../api/api';
import ErrorBanner from '../components/ErrorBanner';
import { CheckIcon } from '../components/Icons';
import PageHeader from '../components/PageHeader';

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
      <PageHeader title="Usuários" subtitle="Cadastre pessoas que podem entrar e reservar salas." />
      <div className="card narrow stack">
        <div className="form-title">
          <h2>Novo usuário</h2>
          <p className="hint">A pessoa entra com este e-mail e esta senha; passe a senha por um canal seguro.</p>
        </div>
        <ErrorBanner error={error} />
        {ok && (
          <div className="ok" role="status">
            <CheckIcon />
            <span>{ok}</span>
          </div>
        )}
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="user-email">E-mail</label>
            <input id="user-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <div className="field">
            <label htmlFor="user-password">Senha</label>
            <input id="user-password" type="password" minLength={8} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="new-password" />
            <p className="hint">Entre 8 e 72 caracteres.</p>
          </div>
          <div className="field">
            <label htmlFor="user-role">Papel</label>
            <select id="user-role" value={role} onChange={(e) => setRole(e.target.value as api.Role)}>
              <option value="MEMBER">MEMBER (reserva salas)</option>
              <option value="ADMIN">ADMIN (gerencia salas e usuários)</option>
            </select>
            <p className="hint">ADMIN também vê e cancela as reservas de todos.</p>
          </div>
          <button type="submit">Criar usuário</button>
        </form>
      </div>
    </section>
  );
}
