export function formatDate(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.valueOf())
    ? "—"
    : new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(date);
}

export function githubUrl(value: string): string | undefined {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname === "github.com"
      ? url.href
      : undefined;
  } catch {
    return undefined;
  }
}
