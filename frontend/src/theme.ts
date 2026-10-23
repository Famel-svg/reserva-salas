export type Theme = 'light' | 'dark';
const KEY = 'theme';

/** Escolha salva pelo usuário, ou null para seguir o sistema. */
export function storedTheme(): Theme | null {
  try {
    const t = localStorage.getItem(KEY);
    return t === 'light' || t === 'dark' ? t : null;
  } catch {
    return null;
  }
}

/** Tema em uso: o forçado no <html> ou, sem ele, o do sistema. */
export function currentTheme(): Theme {
  const forced = document.documentElement.dataset.theme;
  if (forced === 'light' || forced === 'dark') return forced;
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function applyTheme(t: Theme) {
  document.documentElement.dataset.theme = t;
  try {
    localStorage.setItem(KEY, t);
  } catch {
    /* localStorage indisponível: vale só nesta página */
  }
}
