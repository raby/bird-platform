// The species-search feature — the first real dogfood of @raby/ripple. State is signals; the
// async search is an `effect` that debounces, aborts the previous request, and writes its outcome
// into signals; derived UI text is `computed`; the DOM tracks it all through the ripple-dom binding.
// The effect's cleanup (cancel the debounce timer + abort the fetch) is ripple's cleanup contract
// exercised for real — the whole point of dogfooding before publishing.
import { batch, computed, effect, signal } from '@raby/ripple'
import type { Disposer } from '@raby/ripple'
import { searchSpecies, type SpeciesResponse } from './api'
import { list, on, scope, show, text } from './dom'
import { el, humanizeStatus } from './util'

const DEBOUNCE_MS = 250

type Status = 'idle' | 'loading' | 'ok' | 'error'

function renderRow(s: SpeciesResponse): HTMLLIElement {
  const en = s.commonNames.find((c) => c.language === 'en')?.name ?? s.scientificName
  const badge = el('span', { class: `badge badge--${s.conservationStatus}` }, humanizeStatus(s.conservationStatus))
  const header = el('div', { class: 'result__head' }, el('span', { class: 'result__name' }, en), badge)
  const sci = el('em', { class: 'result__sci' }, s.scientificName)
  const taxo = el(
    'div',
    { class: 'result__taxo' },
    `${s.taxonomy.order} · ${s.taxonomy.family} · ${s.taxonomy.genus}`,
  )
  return el('li', { class: 'result' }, header, sci, taxo)
}

/** Build the species-search UI into `root` and wire it to ripple. Returns a disposer that stops
 *  every effect/binding and removes the DOM — ripple has no owner tree, so the view owns teardown. */
export function mountSpeciesSearch(root: HTMLElement): Disposer {
  const query = signal('')
  const trimmed = computed(() => query().trim())
  const status = signal<Status>('idle')
  const results = signal<readonly SpeciesResponse[]>([])
  const errorMsg = signal('')

  const statusText = computed(() => {
    switch (status()) {
      case 'loading':
        return 'Searching…'
      case 'error':
        return errorMsg()
      case 'ok': {
        const n = results().length
        return n === 0 ? `No species match “${trimmed()}”` : `${String(n)} result${n === 1 ? '' : 's'}`
      }
      default:
        return trimmed().length === 1 ? 'Type at least 2 characters to search' : ''
    }
  })

  const input = el('input', {
    id: 'species-q',
    type: 'search',
    autocomplete: 'off',
    placeholder: 'Try “robin”, “falcon”, “merula”…',
  })
  const label = el('label', { for: 'species-q' }, 'Search species')
  const form = el('form', { class: 'search', role: 'search' }, label, input)
  const statusLine = el('p', { class: 'status', role: 'status', 'aria-live': 'polite' })
  const resultsList = el('ul', { class: 'results', 'aria-label': 'Species results' })
  const main = el(
    'main',
    { class: 'app' },
    el(
      'header',
      {},
      el('h2', {}, 'Find a species'),
      el('p', { class: 'tagline' }, 'Fuzzy search over the taxonomy read model.'),
    ),
    form,
    statusLine,
    resultsList,
  )
  root.append(main)

  const s = scope()
  s.add(on(form, 'submit', (e) => e.preventDefault()))
  s.add(on(input, 'input', () => query(input.value)))
  s.add(text(statusLine, statusText))
  s.add(show(resultsList, () => status() === 'ok' && results().length > 0))
  s.add(list(resultsList, results, { key: (sp) => sp.id, render: renderRow }))

  // The debounced, abortable search. Re-runs whenever the trimmed query changes.
  s.add(
    effect(() => {
      const q = trimmed()
      if (q.length < 2) {
        batch(() => {
          status('idle')
          results([])
          errorMsg('')
        })
        return
      }
      status('loading')
      const ac = new AbortController()
      const timer = setTimeout(() => {
        void searchSpecies(q, 20, ac.signal)
          .then((r) => {
            if (ac.signal.aborted) return
            batch(() => {
              if (r.ok) {
                status('ok')
                results(r.species)
                errorMsg('')
              } else {
                status('error')
                results([])
                errorMsg(r.error.message)
              }
            })
          })
          .catch((err: unknown) => {
            // An aborted request (superseded query / teardown) is expected — ignore it.
            // A real transport failure surfaces as an error state.
            if (ac.signal.aborted) return
            batch(() => {
              status('error')
              results([])
              errorMsg(err instanceof Error ? err.message : 'Search request failed')
            })
          })
      }, DEBOUNCE_MS)
      return () => {
        clearTimeout(timer)
        ac.abort()
      }
    }),
  )

  return () => {
    s.dispose()
    main.remove()
  }
}
