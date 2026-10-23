import { useState } from 'react';
import { applyTheme, currentTheme } from '../theme';
import { MoonIcon, SunIcon } from './Icons';

/** Alterna claro/escuro; a escolha fica em data-theme no <html> e no localStorage. */
export default function ThemeToggle() {
  const [theme, setTheme] = useState(currentTheme);
  const dark = theme === 'dark';
  return (
    <button
      type="button"
      className="ghost icon-btn"
      aria-pressed={dark}
      aria-label="Tema escuro"
      title={dark ? 'Usar tema claro' : 'Usar tema escuro'}
      onClick={() => {
        const next = dark ? 'light' : 'dark';
        applyTheme(next);
        setTheme(next);
      }}
    >
      {dark ? <SunIcon /> : <MoonIcon />}
    </button>
  );
}
