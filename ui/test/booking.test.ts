import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountBookingFlow } from '../src/booking'
import { __resetMockBookings } from '../src/mock'

describe('booking flow (dogfooding ripple end to end)', () => {
  let root: HTMLElement
  let dispose: () => void

  beforeEach(() => {
    vi.useFakeTimers()
    __resetMockBookings()
    root = document.createElement('div')
    document.body.append(root)
    dispose = mountBookingFlow(root)
  })
  afterEach(() => {
    dispose()
    root.remove()
    vi.useRealTimers()
  })

  const settle = async (): Promise<void> => {
    await vi.advanceTimersByTimeAsync(300)
    await Promise.resolve()
  }
  const q = <T extends Element>(sel: string): T | null => root.querySelector<T>(sel)
  const submitForm = (): void => {
    q<HTMLFormElement>('.booking-form')?.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }))
  }
  const clickButton = (label: string): void => {
    const btn = Array.from(root.querySelectorAll('button')).find((b) => b.textContent === label)
    if (!btn) throw new Error(`button not found: ${label}`)
    btn.click()
  }

  it('loads hides and shows availability for the default selection', async () => {
    await settle()
    const hideOptions = Array.from(root.querySelectorAll('#hide option'))
    expect(hideOptions.length).toBe(4)
    expect(q('.availability')?.textContent).toContain('6 of 6 seats free')
  })

  it('requests a booking and renders the versioned card', async () => {
    await settle()
    submitForm()
    await settle()

    const card = q('.booking-card')
    expect(card).not.toBeNull()
    expect(card?.textContent).toContain('Kingfisher Hide')
    expect(card?.textContent).toContain('Requested')
    expect(card?.textContent).toContain('version 0')
    // the request form is now hidden
    expect(q('.booking-form')).toBeNull()
  })

  it('confirms the booking, bumping the version to 1', async () => {
    await settle()
    submitForm()
    await settle()
    clickButton('Confirm')
    await settle()

    const card = q('.booking-card')
    expect(card?.textContent).toContain('Confirmed')
    expect(card?.textContent).toContain('version 1')
  })

  it('cancels the booking', async () => {
    await settle()
    submitForm()
    await settle()
    clickButton('Cancel')
    await settle()

    expect(q('.booking-card')?.textContent).toContain('Cancelled')
    expect(q('.booking-card')?.textContent).toContain('version 1')
  })

  it('returns to the form on “New booking”', async () => {
    await settle()
    submitForm()
    await settle()
    expect(q('.booking-form')).toBeNull()

    clickButton('New booking')
    await settle()
    expect(q('.booking-form')).not.toBeNull()
    expect(q('.booking-card')).toBeNull()
  })
})
