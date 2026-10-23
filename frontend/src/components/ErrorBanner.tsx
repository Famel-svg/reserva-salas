import { errorParts } from '../lib';
import { AlertIcon } from './Icons';

export default function ErrorBanner({ error }: { error: unknown }) {
  if (!error) return null;
  const { title, detail } = errorParts(error);
  return (
    <div className="error" role="alert">
      <AlertIcon />
      <span>
        <strong>{title}</strong>
        {detail && <span> {detail}</span>}
      </span>
    </div>
  );
}
