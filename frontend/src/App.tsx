import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { CalendarIcon, LogoIcon, LogoutIcon, OverviewIcon, RoomIcon, UsersIcon } from './components/Icons';
import DashboardPage from './pages/DashboardPage';
import LoginPage from './pages/LoginPage';
import ReservationsPage from './pages/ReservationsPage';
import RoomsPage from './pages/RoomsPage';
import UsersPage from './pages/UsersPage';

// O token só expõe o papel; é ele que identifica a sessão na interface.
const roleName = { ADMIN: 'Administrador', MEMBER: 'Membro' } as const;

export default function App() {
  const { role, signOut } = useAuth();
  if (!role) return <LoginPage />;
  return (
    <div className="shell">
      <aside className="sidebar">
        <span className="brand">
          <LogoIcon />
          Reserva de Salas
        </span>
        {/* NavLink marca a página atual com aria-current="page". */}
        <nav aria-label="Principal" className="nav">
          <NavLink to="/overview"><OverviewIcon />Visão geral</NavLink>
          <NavLink to="/rooms"><RoomIcon />Salas</NavLink>
          <NavLink to="/reservations"><CalendarIcon />Reservas</NavLink>
          {role === 'ADMIN' && <NavLink to="/users"><UsersIcon />Usuários</NavLink>}
        </nav>
        <div className="session">
          <span className="avatar" aria-hidden="true">{roleName[role][0]}</span>
          <span className="who">
            <strong>{roleName[role]}</strong>
            <span className="role">{role}</span>
          </span>
          <button className="ghost icon-btn" onClick={() => void signOut()} aria-label="Sair" title="Sair">
            <LogoutIcon />
          </button>
        </div>
      </aside>
      <main className="page">
        <Routes>
          <Route path="/overview" element={<DashboardPage />} />
          <Route path="/rooms" element={<RoomsPage />} />
          <Route path="/reservations" element={<ReservationsPage />} />
          {role === 'ADMIN' && <Route path="/users" element={<UsersPage />} />}
          <Route path="*" element={<Navigate to="/overview" replace />} />
        </Routes>
      </main>
    </div>
  );
}
