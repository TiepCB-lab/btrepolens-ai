import { AlertCircle, RefreshCw } from "lucide-react";

export function Loading({ label = "Loading data" }: { label?: string }) {
  return (
    <div className="state" role="status">
      <span className="loader" aria-hidden="true" />
      {label}…
    </div>
  );
}

export function ErrorState({
  error,
  retry,
}: {
  error: Error;
  retry?: () => void;
}) {
  return (
    <div className="state error" role="alert">
      <AlertCircle size={17} aria-hidden="true" />
      <span>{error.message}</span>
      {retry && (
        <button className="text-button" onClick={retry}>
          <RefreshCw size={14} /> Try again
        </button>
      )}
    </div>
  );
}
