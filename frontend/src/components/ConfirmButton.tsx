import { useState } from 'react';

/** Ação destrutiva em dois passos: o primeiro clique só pede confirmação. Esc ou "Voltar" desiste. */
export default function ConfirmButton({ label, confirmLabel, onConfirm }: {
  label: string;
  confirmLabel: string;
  onConfirm: () => void;
}) {
  const [asking, setAsking] = useState(false);
  if (!asking) {
    return (
      <button type="button" className="danger" onClick={() => setAsking(true)}>
        {label}
      </button>
    );
  }
  return (
    <span className="confirm" role="group" aria-label={confirmLabel} onKeyDown={(e) => e.key === 'Escape' && setAsking(false)}>
      <button
        type="button"
        className="danger solid"
        autoFocus
        onClick={() => {
          setAsking(false);
          onConfirm();
        }}
      >
        {confirmLabel}
      </button>
      <button type="button" onClick={() => setAsking(false)}>
        Voltar
      </button>
    </span>
  );
}
