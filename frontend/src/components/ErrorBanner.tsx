import { errorParts } from '../lib';

export default function ErrorBanner({ error }: { error: unknown }) {
  if (!error) return null;
  const { title, detail } = errorParts(error);
  return (
    <div className="error" role="alert">
      <strong>{title}</strong>
      {detail && <span> {detail}</span>}
    </div>
  );
}
