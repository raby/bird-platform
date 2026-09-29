// The version-race demo — the concurrency showpiece. Two panels load the SAME booking at the same
// version; when each confirms, the optimistic lock lets one win (200, CONFIRMED, version + 1) and
// rejects the other with a live 409 VersionConflict. The refresh then re-reads the current booking,
// and the idempotency retry shows the same booking comes back — never a duplicate. Pure ripple over
// the booking API (the in-memory store reproduces the compare-and-swap, so it runs with no backend).
import { batch, signal } from '@raby/ripple'
import type { Disposer } from '@raby/ripple'
import { confirmBooking, createBooking, getBooking, type BookingResponse } from './api'
import { attr, classes, on, scope, show, text } from './dom'

const DEMO_OBSERVER = '22222222-2222-2222-2222-222222222222'
const RACE_HIDE = '10000000-0000-0000-0000-000000000001' // Kingfisher Hide

// A far-future, unique 2-hour slot per race, so a new setup never collides with an earlier booking.
let raceSeq = 0
function uniqueSlot(): { start: string; end: string } {
  raceSeq += 1
  const start = new Date(Date.UTC(2027, 0, 1, 8) + raceSeq * 24 * 60 * 60 * 1000)
  const end = new Date(start.getTime() + 2 * 60 * 60 * 1000)
  return { start: start.toISOString(), end: end.toISOString() }
}

function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Record<string, string> = {},
  ...children: (Node | string)[]
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag)
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v)
  for (const child of children) node.append(child)
  return node
}

function newIdempotencyKey(): string {
  return `race-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

function shortId(id: string): string {
  return id.slice(0, 8)
}

function humanizeStatus(status: string): string {
  return status.charAt(0) + status.slice(1).toLowerCase()
}

type Outcome = 'idle' | 'busy' | 'won' | 'conflict' | 'refreshed'

interface Panel {
  readonly node: HTMLElement
  readonly reset: (b: BookingResponse) => void
}

export function mountRaceDemo(root: HTMLElement): Disposer {
  const s = scope()

  function makePanel(label: string): Panel {
    const held = signal<BookingResponse | null>(null)
    const outcome = signal<Outcome>('idle')
    const note = signal('Waiting for a booking…')

    const statusEl = el('span', { class: 'badge' })
    const versionEl = el('span', { class: 'race-panel__version' })
    const confirmBtn = el('button', { type: 'button', class: 'btn btn--primary' }, `Confirm from ${label}`)
    const refreshBtn = el('button', { type: 'button', class: 'btn' }, 'Refresh')
    const noteEl = el('p', { class: 'race-panel__note', role: 'status', 'aria-live': 'polite' })
    const panelEl = el(
      'div',
      { class: 'race-panel' },
      el('div', { class: 'race-panel__head' }, el('h3', {}, `Panel ${label}`), statusEl),
      versionEl,
      el('div', { class: 'race-panel__actions' }, confirmBtn, refreshBtn),
      noteEl,
    )

    async function confirm(): Promise<void> {
      const b = held()
      if (b === null || outcome() === 'busy' || outcome() === 'won' || b.status !== 'REQUESTED') return
      batch(() => {
        outcome('busy')
        note('Confirming…')
      })
      const res = await confirmBooking(b.id, b.version)
      batch(() => {
        if (res.ok) {
          held(res.booking)
          outcome('won')
          note(`Won the lock — confirmed at version ${String(res.booking.version)}.`)
        } else {
          outcome('conflict')
          note(`${res.error.code} — this panel held version ${String(b.version)}, which is now stale.`)
        }
      })
    }

    async function refresh(): Promise<void> {
      const b = held()
      if (b === null) return
      note('Refreshing…')
      const res = await getBooking(b.id)
      batch(() => {
        if (res.ok) {
          held(res.booking)
          outcome('refreshed')
          note(`Refreshed — ${res.booking.status.toLowerCase()} at version ${String(res.booking.version)}.`)
        } else {
          note(res.error.message)
        }
      })
    }

    s.add(text(statusEl, () => {
      const b = held()
      return b === null ? '—' : humanizeStatus(b.status)
    }))
    s.add(classes(statusEl, {
      'badge--requested': () => held()?.status === 'REQUESTED',
      'badge--confirmed': () => held()?.status === 'CONFIRMED',
      'badge--cancelled': () => held()?.status === 'CANCELLED',
    }))
    s.add(text(versionEl, () => {
      const b = held()
      return b === null ? '' : `holding version ${String(b.version)}`
    }))
    s.add(classes(panelEl, {
      'race-panel--won': () => outcome() === 'won',
      'race-panel--conflict': () => outcome() === 'conflict',
    }))
    s.add(attr(confirmBtn, 'disabled', () => {
      const b = held()
      return b === null || outcome() === 'busy' || outcome() === 'won' || b.status !== 'REQUESTED'
    }))
    s.add(show(refreshBtn, () => outcome() === 'conflict'))
    s.add(on(confirmBtn, 'click', () => void confirm()))
    s.add(on(refreshBtn, 'click', () => void refresh()))
    s.add(text(noteEl, note))

    const reset = (b: BookingResponse): void => {
      batch(() => {
        held(b)
        outcome('idle')
        note('Loaded — ready to confirm.')
      })
    }

    return { node: panelEl, reset }
  }

  const panelA = makePanel('A')
  const panelB = makePanel('B')

  const hasRace = signal(false)
  const setupNote = signal('Set up a booking, then confirm from both panels.')
  const idemNote = signal('')
  let currentKey = ''
  let currentSlot = { start: '', end: '' }

  const newRaceBtn = el('button', { type: 'button', class: 'btn btn--primary' }, 'New race')
  const retryBtn = el('button', { type: 'button', class: 'btn' }, 'Retry create (same key)')
  const setupNoteEl = el('p', { class: 'race-setup__note', role: 'status', 'aria-live': 'polite' })
  const idemNoteEl = el('p', { class: 'race-idem__note', role: 'status', 'aria-live': 'polite' })

  async function newRace(): Promise<void> {
    batch(() => {
      setupNote('Setting up a booking…')
      idemNote('')
    })
    const key = newIdempotencyKey()
    const slot = uniqueSlot()
    const res = await createBooking({
      idempotencyKey: key,
      hideId: RACE_HIDE,
      observerId: DEMO_OBSERVER,
      slotStart: slot.start,
      slotEnd: slot.end,
      partySize: 1,
    })
    if (res.ok) {
      currentKey = key
      currentSlot = slot
      panelA.reset(res.booking)
      panelB.reset(res.booking)
      batch(() => {
        hasRace(true)
        setupNote(
          `Both panels loaded booking ${shortId(res.booking.id)} at version ${String(res.booking.version)}. ` +
            'Confirm from each — one wins, the other gets a 409.',
        )
      })
    } else {
      setupNote(res.error.message)
    }
  }

  async function retryCreate(): Promise<void> {
    if (currentKey === '') return
    idemNote('Retrying create with the same idempotency key…')
    const res = await createBooking({
      idempotencyKey: currentKey,
      hideId: RACE_HIDE,
      observerId: DEMO_OBSERVER,
      slotStart: currentSlot.start,
      slotEnd: currentSlot.end,
      partySize: 1,
    })
    if (res.ok) {
      idemNote(
        `Same booking returned — ${shortId(res.booking.id)} ` +
          `(${res.booking.status.toLowerCase()}, version ${String(res.booking.version)}). No duplicate created.`,
      )
    } else {
      idemNote(res.error.message)
    }
  }

  const main = el(
    'main',
    { class: 'app' },
    el(
      'header',
      {},
      el('h2', {}, 'Version race'),
      el('p', { class: 'tagline' }, 'Two panels, one booking, one optimistic lock.'),
    ),
    el('div', { class: 'race-setup' }, newRaceBtn, setupNoteEl),
    el('div', { class: 'race-panels' }, panelA.node, panelB.node),
    el(
      'section',
      { class: 'race-idem' },
      el('h3', {}, 'Idempotency'),
      el('p', { class: 'tagline' }, 'Retrying the create with the same key returns the same booking.'),
      retryBtn,
      idemNoteEl,
    ),
  )
  root.append(main)

  s.add(on(newRaceBtn, 'click', () => void newRace()))
  s.add(on(retryBtn, 'click', () => void retryCreate()))
  s.add(attr(retryBtn, 'disabled', () => !hasRace()))
  s.add(text(setupNoteEl, setupNote))
  s.add(text(idemNoteEl, idemNote))

  return () => {
    s.dispose()
    main.remove()
  }
}
