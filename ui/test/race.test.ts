import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetMockBookings } from '../src/mock'
import { mountRaceDemo } from '../src/race'

describe('version-race demo (dogfooding ripple end to end)', () => {
  let root: HTMLElement
  let dispose: () => void

  beforeEach(() => {
    vi.useFakeTimers()
    __resetMockBookings()
    root = document.createElement('div')
    document.body.append(root)
    dispose = mountRaceDemo(root)
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
  const click = (label: string): void => {
    const btn = Array.from(root.querySelectorAll('button')).find((b) => b.textContent === label)
    if (!btn) throw new Error(`button not found: ${label}`)
    btn.click()
  }
  const panels = (): Element[] => Array.from(root.querySelectorAll('.race-panel'))

  it('loads both panels at the same version', async () => {
    click('New race')
    await settle()

    const [a, b] = panels()
    expect(a?.textContent).toContain('Requested')
    expect(a?.textContent).toContain('holding version 0')
    expect(b?.textContent).toContain('holding version 0')
  })

  it('one panel wins the optimistic lock; the other gets a live version conflict', async () => {
    click('New race')
    await settle()

    click('Confirm from A')
    await settle()
    const a = panels()[0]
    expect(a?.className).toContain('race-panel--won')
    expect(a?.textContent).toContain('Confirmed')
    expect(a?.textContent).toContain('holding version 1')
    expect(a?.textContent).toContain('Won the lock')

    click('Confirm from B')
    await settle()
    const b = panels()[1]
    expect(b?.className).toContain('race-panel--conflict')
    expect(b?.textContent).toContain('VersionConflict')
    expect(b?.textContent).toContain('holding version 0') // still stale
  })

  it('the losing panel can refresh to the current confirmed state', async () => {
    click('New race')
    await settle()
    click('Confirm from A')
    await settle()
    click('Confirm from B')
    await settle()

    click('Refresh')
    await settle()
    const b = panels()[1]
    expect(b?.textContent).toContain('Confirmed')
    expect(b?.textContent).toContain('holding version 1')
  })

  it('an idempotent retry returns the same booking, not a duplicate', async () => {
    click('New race')
    await settle()
    const setupNote = root.querySelector('.race-setup__note')?.textContent ?? ''
    const idMatch = /booking (\w{8})/.exec(setupNote)
    expect(idMatch).not.toBeNull()

    click('Retry create (same key)')
    await settle()
    const idemNote = root.querySelector('.race-idem__note')?.textContent ?? ''
    expect(idemNote).toContain('Same booking returned')
    expect(idemNote).toContain('No duplicate')
    if (idMatch) expect(idemNote).toContain(idMatch[1])
  })
})
