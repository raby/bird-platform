// A tiny reactive DOM binding built entirely on @raby/ripple. Not a framework: each helper is
// just an `effect` (or a plain listener) that writes to the DOM when a signal it reads changes.
// No VDOM, no diff engine — the Solid model, kept small and legible. This lives in the UI for now;
// if it stays this clean it can be extracted to a `@raby/ripple-dom` companion later.
//
// ripple effects run their initial pass synchronously, so the first render is immediate; later
// updates are microtask-batched (multiple writes in a tick coalesce into one DOM update).
import { effect, untrack, type Disposer } from '@raby/ripple'

/** A reactive read: a zero-arg function that reads one or more signals. */
export type Accessor<T> = () => T

/** `node.textContent` tracks the accessor. */
export function text(node: Node, value: Accessor<string | number>): Disposer {
  return effect(() => {
    node.textContent = String(value())
  })
}

/**
 * An attribute tracks the accessor. `false`/`null`/`undefined` removes it; `true` sets it empty
 * (boolean attribute); anything else is stringified. Good for `disabled`, `aria-*`, `href`, …
 */
export function attr(
  el: Element,
  name: string,
  value: Accessor<string | number | boolean | null | undefined>,
): Disposer {
  return effect(() => {
    const v = value()
    if (v === false || v === null || v === undefined) el.removeAttribute(name)
    else if (v === true) el.setAttribute(name, '')
    else el.setAttribute(name, String(v))
  })
}

/** Toggle each class on/off from its accessor: `classes(el, { active: () => isActive() })`. */
export function classes(el: Element, map: Record<string, Accessor<boolean>>): Disposer {
  const disposers = Object.entries(map).map(([name, on]) =>
    effect(() => {
      el.classList.toggle(name, on())
    }),
  )
  return () => disposers.forEach((d) => d())
}

/** Wire a DOM event to a handler (which typically writes a signal). Returns a disposer. */
export function on<K extends keyof HTMLElementEventMap>(
  el: HTMLElement,
  event: K,
  handler: (e: HTMLElementEventMap[K]) => void,
  options?: AddEventListenerOptions,
): Disposer {
  el.addEventListener(event, handler as EventListener, options)
  return () => el.removeEventListener(event, handler as EventListener, options)
}

/** Conditionally keep `node` in the DOM. When the accessor is false the node is detached; when
 *  true it is re-inserted at the slot it started in. `node` must already be placed when called. */
export function show(node: ChildNode, when: Accessor<boolean>): Disposer {
  const anchor = document.createComment('show')
  node.before(anchor)
  return effect(() => {
    if (when()) {
      if (node.parentNode === null) anchor.after(node)
    } else {
      node.remove()
    }
  })
}

/** How a keyed reactive list renders and identifies its rows. */
export interface ListOptions<T> {
  /** Stable identity per item; rows with an unchanged key are reused, not re-rendered. */
  key: (item: T) => string | number
  /** Build the row node. Rows are treated as immutable by key (the search-results use case). */
  render: (item: T) => Node
}

/**
 * A keyed reactive list: `parent`'s rows track the items signal. Rows keep their nodes across
 * updates when their key is unchanged, new keys are rendered, gone keys are removed, and the DOM
 * order is reconciled with a minimal set of moves. Only the `items()` read is a dependency —
 * rendering runs untracked so a row's own reads never subscribe the list itself.
 */
export function list<T>(parent: Node, items: Accessor<readonly T[]>, options: ListOptions<T>): Disposer {
  const { key, render } = options
  const end = document.createComment('list-end')
  parent.appendChild(end)
  let current = new Map<string | number, Node>()

  const dispose = effect(() => {
    const next = items()
    untrack(() => {
      const nextMap = new Map<string | number, Node>()
      const ordered: Node[] = []
      for (const item of next) {
        const k = key(item)
        const node = current.get(k) ?? render(item)
        nextMap.set(k, node)
        ordered.push(node)
      }
      // Drop rows whose key is gone.
      for (const [k, node] of current) {
        if (!nextMap.has(k)) (node as ChildNode).remove()
      }
      // Place rows in order, moving only those out of position (walk back from the end anchor).
      let ref: Node = end
      for (let i = ordered.length - 1; i >= 0; i--) {
        const node = ordered[i]!
        if (node.nextSibling !== ref) parent.insertBefore(node, ref)
        ref = node
      }
      current = nextMap
    })
  })

  return () => {
    dispose()
    for (const node of current.values()) (node as ChildNode).remove()
    end.remove()
    current = new Map()
  }
}

/** Collects disposers for a view and tears them down (in reverse) — ripple has no owner tree, so
 *  the binding owns its own lifecycle. `add` each binding; call `dispose` to unmount the view. */
export function scope(): { add: (d: Disposer) => void; dispose: Disposer } {
  const disposers: Disposer[] = []
  return {
    add: (d: Disposer) => {
      disposers.push(d)
    },
    dispose: () => {
      while (disposers.length > 0) disposers.pop()!()
    },
  }
}
