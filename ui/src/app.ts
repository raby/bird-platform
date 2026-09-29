// App shell: a top bar that switches between the booking flow, the version-race demo and species
// search. The active view is a ripple signal — the nav toggles it, `classes` reflects the active
// tab, and `show` mounts one view at a time. All features stay mounted (state is preserved on switch).
import { signal } from '@raby/ripple'
import type { Disposer } from '@raby/ripple'
import { mountBookingFlow } from './booking'
import { attr, classes, on, scope, show } from './dom'
import { mountRaceDemo } from './race'
import { mountSpeciesSearch } from './search'
import { el } from './util'

type View = 'book' | 'race' | 'search'

export function mountApp(root: HTMLElement): Disposer {
  const view = signal<View>('book')

  const bookTab = el('button', { type: 'button', class: 'tab' }, 'Book a hide')
  const raceTab = el('button', { type: 'button', class: 'tab' }, 'Version race')
  const searchTab = el('button', { type: 'button', class: 'tab' }, 'Find a species')
  const brand = el(
    'div',
    { class: 'brand' },
    el('span', { class: 'brand__name' }, 'bird-platform'),
    el('span', { class: 'brand__tag' }, 'concurrency, made watchable'),
  )
  const topbar = el(
    'header',
    { class: 'topbar' },
    brand,
    el('nav', { class: 'tabs', 'aria-label': 'Views' }, bookTab, raceTab, searchTab),
  )

  const bookView = el('div', { class: 'view' })
  const raceView = el('div', { class: 'view' })
  const searchView = el('div', { class: 'view' })
  const shell = el('div', { class: 'shell' }, topbar, bookView, raceView, searchView)
  root.append(shell)

  const s = scope()
  s.add(mountBookingFlow(bookView))
  s.add(mountRaceDemo(raceView))
  s.add(mountSpeciesSearch(searchView))

  const tabs: { tab: HTMLButtonElement; view: HTMLElement; name: View }[] = [
    { tab: bookTab, view: bookView, name: 'book' },
    { tab: raceTab, view: raceView, name: 'race' },
    { tab: searchTab, view: searchView, name: 'search' },
  ]
  for (const { tab, view: viewEl, name } of tabs) {
    s.add(on(tab, 'click', () => view(name)))
    s.add(classes(tab, { 'tab--active': () => view() === name }))
    s.add(attr(tab, 'aria-current', () => (view() === name ? 'page' : false)))
    s.add(show(viewEl, () => view() === name))
  }

  return () => {
    s.dispose()
    shell.remove()
  }
}
