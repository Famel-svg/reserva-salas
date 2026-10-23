import { useState, type FormEvent } from 'react';
import ErrorBanner from '../components/ErrorBanner';
import { LogoIcon } from '../components/Icons';
import { useAuth } from '../auth/AuthContext';
import ThemeToggle from '../components/ThemeToggle';

export default function LoginPage() {
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await signIn(email, password);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login">
      <div className="card">
        <div className="login-head">
          <div className="login-top">
            <span className="brand">
              <LogoIcon />
              Reserva de Salas
            </span>
            <ThemeToggle />
          </div>
          <h1>Entrar</h1>
          <p className="lede">Use o e-mail e a senha cadastrados pelo administrador.</p>
        </div>
        <ErrorBanner error={error} />
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="login-email">E-mail</label>
            <input id="login-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="username" />
          </div>
          <div className="field">
            <label htmlFor="login-password">Senha</label>
            <input id="login-password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
          </div>
          <button type="submit" disabled={busy}>
            {busy ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
      </div>
    </main>
  );
}
