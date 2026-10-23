// SVG inline (sem dependência de ícones); decorativos, por isso aria-hidden.
const base = { width: 18, height: 18, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true } as const;

/** Porta com calendário: marca da aplicação. */
export const LogoIcon = () => (
  <svg {...base} width={22} height={22}>
    <rect x="3" y="4" width="18" height="17" rx="2" />
    <path d="M3 9h18M8 2v4M16 2v4M10 14h4" />
  </svg>
);

export const AlertIcon = () => (
  <svg {...base}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v6M12 16.5v.5" />
  </svg>
);

export const CheckIcon = () => (
  <svg {...base}>
    <circle cx="12" cy="12" r="9" />
    <path d="m8 12 3 3 5-6" />
  </svg>
);
