import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
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
        <nav aria-label="Principal">
          <NavLink to="/rooms">Salas</NavLink>
          <NavLink to="/reservations">Reservas</NavLink>
          {role === 'ADMIN' && <NavLink to="/users">Usuários</NavLink>}
        </nav>
        <span>
          {role} <button onClick={() => void signOut()}>Sair</button>
        </span>
      </header>
      <div className="page">
        <Routes>
          <Route path="/rooms" element={<RoomsPage />} />
          <Route path="/reservations" element={<ReservationsPage />} />
          {role === 'ADMIN' && <Route path="/users" element={<UsersPage />} />}
          <Route path="*" element={<Navigate to="/rooms" replace />} />
        </Routes>
      </div>
    </>
  );
}
