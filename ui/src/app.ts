// App shell: a top bar that switches between the booking flow and species search. The active view
// is a ripple signal — the nav toggles it, `classes` reflects the active tab, and `show` mounts one
// view at a time. Both features stay mounted (state is preserved when switching).
import { signal } from '@raby/ripple'
import type { Disposer } from '@raby/ripple'
import { mountBookingFlow } from './booking'
import { attr, classes, on, scope, show } from './dom'
import { mountSpeciesSearch } from './search'

type View = 'book' | 'search'

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

export function mountApp(root: HTMLElement): Disposer {
  const view = signal<View>('book')

  const bookTab = el('button', { type: 'button', class: 'tab' }, 'Book a hide')
  const searchTab = el('button', { type: 'button', class: 'tab' }, 'Find a species')
  const brand = el(
    'div',
    { class: 'brand' },
    el('span', { class: 'brand__name' }, 'bird-platform'),
    el('span', { class: 'brand__tag' }, 'concurrency, made watchable'),
  )
  const topbar = el('header', { class: 'topbar' }, brand, el('nav', { class: 'tabs', 'aria-label': 'Views' }, bookTab, searchTab))

  const bookView = el('div', { class: 'view' })
  const searchView = el('div', { class: 'view' })
  const shell = el('div', { class: 'shell' }, topbar, bookView, searchView)
  root.append(shell)

  const s = scope()
  s.add(mountBookingFlow(bookView))
  s.add(mountSpeciesSearch(searchView))

  s.add(on(bookTab, 'click', () => view('book')))
  s.add(on(searchTab, 'click', () => view('search')))
  s.add(classes(bookTab, { 'tab--active': () => view() === 'book' }))
  s.add(classes(searchTab, { 'tab--active': () => view() === 'search' }))
  s.add(attr(bookTab, 'aria-current', () => (view() === 'book' ? 'page' : false)))
  s.add(attr(searchTab, 'aria-current', () => (view() === 'search' ? 'page' : false)))
  s.add(show(bookView, () => view() === 'book'))
  s.add(show(searchView, () => view() === 'search'))

  return () => {
    s.dispose()
    shell.remove()
  }
}
