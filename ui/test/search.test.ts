import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../src/api'
import { mountSpeciesSearch } from '../src/search'

// The feature runs against the in-memory matcher (VITE_API_BASE unset in tests), which mirrors the
// backend's seed data and its ≥2-char rule. Fake timers drive both the 250ms debounce and the
// matcher's simulated latency; a microtask flush lets ripple's effects settle into the DOM.
const flush = (): Promise<void> => Promise.resolve()

describe('species search (dogfooding ripple end to end)', () => {
  let root: HTMLElement
  let dispose: () => void

  beforeEach(() => {
    vi.useFakeTimers()
    root = document.createElement('div')
    document.body.append(root)
    dispose = mountSpeciesSearch(root)
  })

  afterEach(() => {
    dispose()
    root.remove()
    vi.useRealTimers()
  })

  const type = (value: string): void => {
    const input = root.querySelector<HTMLInputElement>('#species-q')
    if (!input) throw new Error('search input not found')
    input.value = value
    input.dispatchEvent(new Event('input'))
  }
  const statusText = (): string | null => root.querySelector('.status')?.textContent ?? null
  const rows = (): Element[] => Array.from(root.querySelectorAll('.result'))

  const settleSearch = async (): Promise<void> => {
    await flush() // effect re-run: status → loading, debounce timer armed
    await vi.advanceTimersByTimeAsync(250) // debounce fires → matcher invoked
    await vi.advanceTimersByTimeAsync(140) // matcher latency resolves
    await flush() // render flush
  }

  it('starts idle with no results', () => {
    expect(rows()).toHaveLength(0)
    expect(statusText()).toBe('')
  })

  it('renders the matching species for a query', async () => {
    type('robin')
    await settleSearch()

    expect(rows()).toHaveLength(1)
    expect(rows()[0]?.textContent).toContain('European Robin')
    expect(rows()[0]?.textContent).toContain('Erithacus rubecula')
    expect(rows()[0]?.textContent).toContain('Least concern')
    expect(statusText()).toBe('1 result')
  })

  it('shows the min-length hint and issues no request for a 1-char query', async () => {
    type('r')
    await flush()
    await vi.advanceTimersByTimeAsync(400)
    await flush()

    expect(rows()).toHaveLength(0)
    expect(statusText()).toBe('Type at least 2 characters to search')
  })

  it('reports an empty result set honestly', async () => {
    type('zzzz')
    await settleSearch()

    expect(rows()).toHaveLength(0)
    expect(statusText()).toBe('No species match “zzzz”')
  })

  it('surfaces a transport failure as an error state (not an unhandled rejection)', async () => {
    const spy = vi.spyOn(api, 'searchSpecies').mockRejectedValueOnce(new Error('network down'))
    type('robin')
    await settleSearch()

    expect(statusText()).toBe('network down')
    expect(rows()).toHaveLength(0)
    spy.mockRestore()
  })

  it('debounces and abandons a superseded query — only the last one lands', async () => {
    type('zzzz') // would return no matches
    await flush() // arms debounce timer A
    type('robin') // within the window: ripple runs the effect cleanup (clears A, aborts), arms B
    await settleSearch() // only B fires

    expect(rows()).toHaveLength(1)
    expect(rows()[0]?.textContent).toContain('European Robin')
    expect(statusText()).toBe('1 result')
  })

  it('tears down cleanly', () => {
    dispose()
    expect(root.querySelector('.app')).toBeNull()
    // afterEach calls dispose() again — must be idempotent (no throw)
  })
})
