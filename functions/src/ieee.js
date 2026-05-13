const PATTERN = /^([0-9A-Fa-f]{2}[:\-]){7}[0-9A-Fa-f]{2}$/;

export function normaliseIeee(raw) {
  if (typeof raw !== "string") return null;
  const trimmed = raw.trim();
  if (!PATTERN.test(trimmed)) return null;
  return trimmed.toUpperCase().replaceAll("-", ":");
}
