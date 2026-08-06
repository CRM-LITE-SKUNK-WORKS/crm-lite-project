/**
 * The sale flow's single money formatter.
 *
 * It existed twice (offer selection + submit order) and the submit confirmation
 * needed it a third time, so it was extracted rather than copied again
 * (scope §4.33). Behaviour is unchanged: two decimals and the ` TL` suffix,
 * exactly what both screens already rendered.
 *
 * 🔴 The client NEVER computes, rounds or converts a price — it formats what
 * the catalog returned, and the authoritative total is `totalAmount` on the 201
 * response, which the server snapshots at creation (ADR-016 §2.4, scope §3.11).
 */
export function formatPrice(value: number): string {
  return `${value.toFixed(2)} TL`;
}
