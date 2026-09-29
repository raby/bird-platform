// In-memory stand-in for the backend's species search, used when VITE_API_BASE is unset so the
// SPA runs with no backend. The data is the exact seed the backend's StubTaxonomyGateway serves
// (contexts/species/.../StubTaxonomyGateway.kt) — same five species, same IUCN statuses, same
// ids (`scientificName` lowercased, spaces → hyphens). The only fidelity trade is Elasticsearch's
// fuzzy ranking → a simple local matcher; that same swap is the design's "demo profile" story.
import type { ErrorResponse, SearchResult, SpeciesResponse } from './api'

const AVES = { kingdom: 'Animalia', phylum: 'Chordata', className: 'Aves' } as const

function species(
  scientificName: string,
  en: string,
  nl: string,
  order: string,
  family: string,
  genus: string,
  conservationStatus: string,
): SpeciesResponse {
  return {
    id: scientificName.toLowerCase().replace(/ /g, '-'),
    scientificName,
    commonNames: [
      { language: 'en', name: en },
      { language: 'nl', name: nl },
    ],
    taxonomy: { ...AVES, order, family, genus },
    conservationStatus,
  }
}

const SEED: readonly SpeciesResponse[] = [
  species('Turdus merula', 'Common Blackbird', 'Merel', 'Passeriformes', 'Turdidae', 'Turdus', 'LEAST_CONCERN'),
  species('Erithacus rubecula', 'European Robin', 'Roodborst', 'Passeriformes', 'Muscicapidae', 'Erithacus', 'LEAST_CONCERN'),
  species('Apus apus', 'Common Swift', 'Gierzwaluw', 'Apodiformes', 'Apodidae', 'Apus', 'LEAST_CONCERN'),
  species('Falco peregrinus', 'Peregrine Falcon', 'Slechtvalk', 'Falconiformes', 'Falconidae', 'Falco', 'LEAST_CONCERN'),
  species('Streptopelia turtur', 'European Turtle Dove', 'Tortelduif', 'Columbiformes', 'Columbidae', 'Streptopelia', 'VULNERABLE'),
]

/** A cheap fuzzy score: exact = 4, prefix = 3, substring = 2, in-order subsequence = 1, else 0. */
function score(haystack: string, needle: string): number {
  const h = haystack.toLowerCase()
  const n = needle.toLowerCase()
  if (h === n) return 4
  if (h.startsWith(n)) return 3
  if (h.includes(n)) return 2
  let i = 0
  for (const ch of h) if (ch === n[i]) i++
  return i === n.length ? 1 : 0
}

function bestScore(s: SpeciesResponse, needle: string): number {
  return Math.max(score(s.scientificName, needle), ...s.commonNames.map((c) => score(c.name, needle)))
}

/** Mirrors SpeciesSearchService: trims, requires ≥ 2 chars (else `InvalidQuery`), caps the limit. */
export function mockSearchSpecies(query: string, limit = 20): Promise<SearchResult> {
  const q = query.trim()
  if (q.length < 2) {
    const error: ErrorResponse = { code: 'InvalidQuery', message: 'query must be at least 2 characters' }
    return Promise.resolve({ ok: false, error })
  }
  const capped = Math.min(Math.max(limit, 1), 100)
  const matches = SEED.map((s) => ({ s, score: bestScore(s, q) }))
    .filter((m) => m.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, capped)
    .map((m) => m.s)
  // A small delay so the loading state is real (the backend would take a network round-trip).
  return new Promise((resolve) => setTimeout(() => resolve({ ok: true, species: matches }), 140))
}
