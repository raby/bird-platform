// Small DOM + formatting helpers shared across the UI features.

/** Create an element with attributes and children — a terse, non-reactive builder. */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Record<string, string> = {},
  ...children: (Node | string)[]
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag)
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v)
  for (const child of children) node.append(child)
  return node
}

/** Humanize an UPPER_SNAKE enum name: `LEAST_CONCERN` → `Least concern`, `REQUESTED` → `Requested`. */
export function humanizeStatus(status: string): string {
  const lower = status.replace(/_/g, ' ').toLowerCase()
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}

/** A valid idempotency key (8–128 chars of `[A-Za-z0-9_-]`, matching the backend rule) with a
 *  feature prefix so keys from different flows are distinguishable. */
export function newIdempotencyKey(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

/** First 8 characters of a UUID, for compact display. */
export function shortId(id: string): string {
  return id.slice(0, 8)
}
