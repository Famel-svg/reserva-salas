import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { LogoIcon } from './components/Icons';
import LoginPage from './pages/LoginPage';
import ReservationsPage from './pages/ReservationsPage';
import RoomsPage from './pages/RoomsPage';
import UsersPage from './pages/UsersPage';

export default function App() {
  const { role, signOut } = useAuth();
  if (!role) return <LoginPage />;
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <span className="brand">
            <LogoIcon />
            Reserva de Salas
          </span>
          <nav aria-label="Principal">
            <NavLink to="/rooms">Salas</NavLink>
            <NavLink to="/reservations">Reservas</NavLink>
            {role === 'ADMIN' && <NavLink to="/users">Usuários</NavLink>}
          </nav>
          <div className="session">
            <span className="role" title="Seu papel">{role}</span>
            <button className="ghost" onClick={() => void signOut()}>Sair</button>
          </div>
        </div>
      </header>
      <main className="page">
        <Routes>
          <Route path="/rooms" element={<RoomsPage />} />
          <Route path="/reservations" element={<ReservationsPage />} />
          {role === 'ADMIN' && <Route path="/users" element={<UsersPage />} />}
          <Route path="*" element={<Navigate to="/rooms" replace />} />
        </Routes>
      </main>
    </>
  );
}
