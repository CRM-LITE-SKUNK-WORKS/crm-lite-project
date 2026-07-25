/**
 * Date helpers for the EDS DatePicker. Pure functions, no `Date` timezone
 * traps on the wire: the transport format is ALWAYS ISO `yyyy-MM-dd` and the
 * display format is ALWAYS `dd.MM.yyyy` in both languages (FE-ADR-012 §e
 * recorded exception; scope-and-conflicts §4.10). `Date` objects are used only
 * for calendar math (weekday/grid), constructed with local y/m/d parts.
 */

export interface DateParts {
  readonly year: number;
  readonly month: number; // 1-12
  readonly day: number; // 1-31
}

const ISO_RE = /^(\d{4})-(\d{2})-(\d{2})$/;
const DISPLAY_RE = /^(\d{2})\.(\d{2})\.(\d{4})$/;

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function isRealDate({ year, month, day }: DateParts): boolean {
  const probe = new Date(year, month - 1, day);
  return probe.getFullYear() === year && probe.getMonth() === month - 1 && probe.getDate() === day;
}

export function isoToParts(iso: string): DateParts | null {
  const match = ISO_RE.exec(iso);
  if (!match) return null;
  const parts = { year: +match[1], month: +match[2], day: +match[3] };
  return isRealDate(parts) ? parts : null;
}

export function partsToIso({ year, month, day }: DateParts): string {
  return `${year}-${pad(month)}-${pad(day)}`;
}

/** ISO → `dd.MM.yyyy` for display; invalid/empty input renders as ''. */
export function isoToDisplay(iso: string | null): string {
  if (!iso) return '';
  const parts = isoToParts(iso);
  return parts ? `${pad(parts.day)}.${pad(parts.month)}.${parts.year}` : '';
}

/** The mock's typing mask (DatePicker.jsx `parseTyped`): keep digits, insert
 *  dots after DD and MM, cap at 8 digits. */
export function maskTyped(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 8);
  let out = digits.slice(0, 2);
  if (digits.length > 2) out += '.' + digits.slice(2, 4);
  if (digits.length > 4) out += '.' + digits.slice(4, 8);
  return out;
}

/** Full `dd.MM.yyyy` → ISO, only if it is a real calendar date. */
export function displayToIso(display: string): string | null {
  const match = DISPLAY_RE.exec(display);
  if (!match) return null;
  const parts = { day: +match[1], month: +match[2], year: +match[3] };
  return isRealDate(parts) ? partsToIso(parts) : null;
}

export function todayParts(): DateParts {
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() + 1, day: now.getDate() };
}

export function addDays(parts: DateParts, days: number): DateParts {
  const date = new Date(parts.year, parts.month - 1, parts.day + days);
  return { year: date.getFullYear(), month: date.getMonth() + 1, day: date.getDate() };
}

export function addMonths(parts: DateParts, months: number): DateParts {
  const raw = new Date(parts.year, parts.month - 1 + months, 1);
  const daysInTarget = new Date(raw.getFullYear(), raw.getMonth() + 1, 0).getDate();
  return {
    year: raw.getFullYear(),
    month: raw.getMonth() + 1,
    day: Math.min(parts.day, daysInTarget),
  };
}

/** 0 = Monday … 6 = Sunday (the mock's grid starts on Monday). */
export function mondayIndex(parts: DateParts): number {
  return (new Date(parts.year, parts.month - 1, parts.day).getDay() + 6) % 7;
}

export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

export function compareIso(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}
