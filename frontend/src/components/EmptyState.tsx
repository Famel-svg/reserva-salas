import type { ReactNode } from 'react';

/** Estado vazio: ícone, frase curta e o próximo passo. */
export default function EmptyState({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <div className="empty">
      <span className="empty-icon">{icon}</span>
      <strong>{title}</strong>
      <span>{children}</span>
    </div>
  );
}
