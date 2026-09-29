// The booking flow — ripple dogfooded across a real multi-step, stateful flow: pick a hide and
// slot, see live availability, request a booking, then confirm or cancel it. The booking carries a
// version; confirm/cancel send it as the optimistic-lock expected version, and a stale one surfaces
// as a 409 (the concurrency showpiece the race demo builds on). State is signals, derived text is
// computed, async steps are effects/handlers whose cleanup aborts in-flight requests.
import { batch, computed, effect, signal } from '@raby/ripple'
import type { Disposer } from '@raby/ripple'
import {
  cancelBooking,
  confirmBooking,
  createBooking,
  getAvailability,
  listHides,
  type BookingResponse,
  type BookingResult,
  type HideAvailabilityResponse,
  type HideResponse,
} from './api'
import { attr, classes, on, scope, show, text } from './dom'

// The demo backend has no auth, so bookings are made as one fixed seeded observer.
const DEMO_OBSERVER = '22222222-2222-2222-2222-222222222222'

interface Slot {
  readonly label: string
  readonly start: string
  readonly end: string
}

function slot(daysAhead: number, hour: number): Slot {
  const start = new Date()
  start.setUTCDate(start.getUTCDate() + daysAhead)
  start.setUTCHours(hour, 0, 0, 0)
  const end = new Date(start)
  end.setUTCHours(hour + 2)
  const hh = (h: number): string => String(h).padStart(2, '0')
  const day = start.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })
  return { label: `${day}, ${hh(hour)}:00–${hh(hour + 2)}:00`, start: start.toISOString(), end: end.toISOString() }
}

const SLOTS: readonly Slot[] = [slot(1, 8), slot(1, 14), slot(2, 8), slot(2, 14)]

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

function field(id: string, label: string, input: HTMLElement): HTMLElement {
  return el('label', { class: 'field', for: id }, el('span', { class: 'field__label' }, label), input)
}

function newIdempotencyKey(): string {
  return `book-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

function clampParty(n: number): number {
  if (Number.isNaN(n)) return 1
  return Math.min(Math.max(Math.trunc(n), 1), 20)
}

function humanizeStatus(status: string): string {
  return status.charAt(0) + status.slice(1).toLowerCase()
}

function shortId(id: string): string {
  return id.slice(0, 8)
}

function slotLabelFor(b: BookingResponse): string {
  const match = SLOTS.find((s) => s.start === b.slotStart)
  return match?.label ?? `${new Date(b.slotStart).toLocaleString()} – ${new Date(b.slotEnd).toLocaleString()}`
}

export function mountBookingFlow(root: HTMLElement): Disposer {
  const hides = signal<readonly HideResponse[]>([])
  const hideId = signal('')
  const slotIndex = signal(0)
  const partySize = signal(2)
  const availability = signal<HideAvailabilityResponse | null>(null)
  const booking = signal<BookingResponse | null>(null)
  const busy = signal(false)
  const message = signal('')

  const selectedSlot = computed<Slot | undefined>(() => SLOTS[slotIndex()])
  const availabilityText = computed(() => {
    const a = availability()
    return a === null ? '' : `${String(a.seatsLeft)} of ${String(a.capacity)} seats free for this slot`
  })
  const hideName = (id: string): string => hides().find((h) => h.id === id)?.name ?? id

  // --- form ---
  const hideSelect = el('select', { id: 'hide', class: 'field__input' })
  const slotSelect = el('select', { id: 'slot', class: 'field__input' })
  for (const [i, s] of SLOTS.entries()) slotSelect.append(el('option', { value: String(i) }, s.label))
  const partyInput = el('input', { id: 'party', class: 'field__input', type: 'number', min: '1', max: '20', value: '2' })
  const availabilityLine = el('p', { class: 'availability', 'aria-live': 'polite' })
  const requestBtn = el('button', { type: 'submit', class: 'btn btn--primary' }, 'Request booking')
  const form = el(
    'form',
    { class: 'booking-form' },
    field('hide', 'Hide', hideSelect),
    field('slot', 'Slot', slotSelect),
    field('party', 'Party size', partyInput),
    availabilityLine,
    requestBtn,
  )

  // --- booking card ---
  const cardHide = el('span', { class: 'booking-card__hide' })
  const cardStatus = el('span', { class: 'badge' })
  const cardVersion = el('span', { class: 'booking-card__version' })
  const cardSlot = el('div', { class: 'booking-card__slot' })
  const cardMeta = el('div', { class: 'booking-card__meta' })
  const confirmBtn = el('button', { type: 'button', class: 'btn btn--primary' }, 'Confirm')
  const cancelBtn = el('button', { type: 'button', class: 'btn' }, 'Cancel')
  const newBtn = el('button', { type: 'button', class: 'btn btn--ghost' }, 'New booking')
  const card = el(
    'div',
    { class: 'booking-card' },
    el('div', { class: 'booking-card__head' }, cardHide, cardStatus),
    cardSlot,
    cardMeta,
    cardVersion,
    el('div', { class: 'booking-actions' }, confirmBtn, cancelBtn, newBtn),
  )

  const messageLine = el('p', { class: 'booking-msg', role: 'status', 'aria-live': 'polite' })
  const main = el(
    'main',
    { class: 'app' },
    el(
      'header',
      {},
      el('h2', {}, 'Book a hide'),
      el('p', { class: 'tagline' }, 'Reserve a slot; the booking is versioned and confirmable.'),
    ),
    form,
    card,
    messageLine,
  )
  root.append(main)

  // --- actions ---
  async function requestBooking(): Promise<void> {
    const hid = hideId()
    const sl = selectedSlot()
    if (hid === '' || sl === undefined) return
    batch(() => {
      busy(true)
      message('Requesting…')
    })
    const res = await createBooking({
      idempotencyKey: newIdempotencyKey(),
      hideId: hid,
      observerId: DEMO_OBSERVER,
      slotStart: sl.start,
      slotEnd: sl.end,
      partySize: partySize(),
    })
    batch(() => {
      busy(false)
      if (res.ok) {
        booking(res.booking)
        message(`Requested — booking ${shortId(res.booking.id)} awaits confirmation.`)
      } else {
        message(`${res.error.code}: ${res.error.message}`)
      }
    })
  }

  function applyMutation(res: BookingResult, onOk: (b: BookingResponse) => string): void {
    batch(() => {
      busy(false)
      if (res.ok) {
        booking(res.booking)
        message(onOk(res.booking))
      } else {
        // A stale version lands here as VersionConflict — the optimistic-lock story.
        message(`${res.error.code}: ${res.error.message}`)
      }
    })
  }

  async function confirm(): Promise<void> {
    const b = booking()
    if (b === null) return
    batch(() => {
      busy(true)
      message('Confirming…')
    })
    applyMutation(await confirmBooking(b.id, b.version), (bk) => `Confirmed — now version ${String(bk.version)}.`)
  }

  async function cancel(): Promise<void> {
    const b = booking()
    if (b === null) return
    batch(() => {
      busy(true)
      message('Cancelling…')
    })
    applyMutation(await cancelBooking(b.id, b.version), (bk) => `Cancelled — now version ${String(bk.version)}.`)
  }

  function newBooking(): void {
    batch(() => {
      booking(null)
      message('')
    })
  }

  // --- bindings ---
  const s = scope()
  s.add(on(hideSelect, 'change', () => hideId(hideSelect.value)))
  s.add(on(slotSelect, 'change', () => slotIndex(Number(slotSelect.value))))
  s.add(on(partyInput, 'input', () => partySize(clampParty(Number(partyInput.value)))))
  s.add(text(availabilityLine, availabilityText))
  s.add(attr(requestBtn, 'disabled', () => busy() || hideId() === ''))
  s.add(on(form, 'submit', (e) => {
    e.preventDefault()
    void requestBooking()
  }))

  s.add(show(form, () => booking() === null))
  s.add(show(card, () => booking() !== null))

  s.add(text(cardHide, () => {
    const b = booking()
    return b === null ? '' : hideName(b.hideId)
  }))
  s.add(text(cardStatus, () => {
    const b = booking()
    return b === null ? '' : humanizeStatus(b.status)
  }))
  s.add(classes(cardStatus, {
    'badge--requested': () => booking()?.status === 'REQUESTED',
    'badge--confirmed': () => booking()?.status === 'CONFIRMED',
    'badge--cancelled': () => booking()?.status === 'CANCELLED',
  }))
  s.add(text(cardVersion, () => {
    const b = booking()
    return b === null ? '' : `version ${String(b.version)}`
  }))
  s.add(text(cardSlot, () => {
    const b = booking()
    return b === null ? '' : slotLabelFor(b)
  }))
  s.add(text(cardMeta, () => {
    const b = booking()
    return b === null ? '' : `Party of ${String(b.partySize)} · booking ${shortId(b.id)}`
  }))
  s.add(show(confirmBtn, () => booking()?.status === 'REQUESTED' && !busy()))
  s.add(show(cancelBtn, () => {
    const status = booking()?.status
    return (status === 'REQUESTED' || status === 'CONFIRMED') && !busy()
  }))
  s.add(on(confirmBtn, 'click', () => void confirm()))
  s.add(on(cancelBtn, 'click', () => void cancel()))
  s.add(on(newBtn, 'click', () => {
    newBooking()
  }))
  s.add(text(messageLine, message))

  // Live availability for the chosen hide + slot (aborts a superseded request via effect cleanup).
  s.add(
    effect(() => {
      const hid = hideId()
      const sl = selectedSlot()
      if (booking() !== null || hid === '' || sl === undefined) {
        availability(null)
        return
      }
      const ac = new AbortController()
      void getAvailability(hid, sl.start, sl.end, ac.signal)
        .then((a) => {
          if (!ac.signal.aborted) availability(a)
        })
        .catch(() => {
          if (!ac.signal.aborted) availability(null)
        })
      return () => ac.abort()
    }),
  )

  // Load the hides, populate the select, default to the first.
  void listHides()
    .then((loaded) => {
      for (const h of loaded) {
        hideSelect.append(el('option', { value: h.id }, `${h.name} — ${h.reserve} (${String(h.capacity)} seats)`))
      }
      batch(() => {
        hides(loaded)
        if (loaded.length > 0 && hideId() === '') hideId(loaded[0]!.id)
      })
      hideSelect.value = hideId()
    })
    .catch(() => {
      message('Could not load hides.')
    })

  return () => {
    s.dispose()
    main.remove()
  }
}
