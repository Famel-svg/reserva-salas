import type { ReactNode } from 'react';

/** Título e subtítulo à esquerda, ação primária à direita (abaixo, no celular). */
export default function PageHeader({ title, subtitle, action }: { title: string; subtitle?: ReactNode; action?: ReactNode }) {
  return (
    <header className="page-head">
      <div className="page-title">
        <h1>{title}</h1>
        {subtitle && <p className="lede">{subtitle}</p>}
      </div>
      {action}
    </header>
  );
}
