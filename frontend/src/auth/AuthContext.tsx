import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import * as api from '../api/api';
import { getTokens, logout as apiLogout, setAuthLostHandler } from '../api/client';

export interface AuthValue {
  role: api.Role | null;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

export const AuthContext = createContext<AuthValue>({
  role: null,
  signIn: async () => {},
  signOut: async () => {},
});
export const useAuth = () => useContext(AuthContext);

/** Le a claim "role" do access token (so para a UI; o backend e a autoridade). */
function roleFromToken(): api.Role | null {
  const t = getTokens();
  if (!t) return null;
  try {
    const payload = JSON.parse(atob(t.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload.role === 'ADMIN' || payload.role === 'MEMBER' ? payload.role : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [role, setRole] = useState<api.Role | null>(roleFromToken);

  useEffect(() => setAuthLostHandler(() => setRole(null)), []);

  const signIn = useCallback(async (email: string, password: string) => {
    await api.login(email, password);
    setRole(roleFromToken());
  }, []);
  const signOut = useCallback(async () => {
    await apiLogout();
    setRole(null);
  }, []);

  const value = useMemo(() => ({ role, signIn, signOut }), [role, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
