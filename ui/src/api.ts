// Typed client for the bird-platform REST API. The types mirror the Kotlin DTOs exactly
// (species/adapter/inbound/web/SpeciesDtos.kt). With VITE_API_BASE set, it calls the real
// controllers; unset, it falls back to the in-memory matcher so the SPA runs with no backend.
import { mockSearchSpecies } from './mock'

/** Mirrors `SpeciesResponse` (Kotlin). `conservationStatus` is an IUCN enum name, e.g. `VULNERABLE`. */
export interface SpeciesResponse {
  readonly id: string
  readonly scientificName: string
  readonly commonNames: readonly CommonName[]
  readonly taxonomy: Taxonomy
  readonly conservationStatus: string
}

export interface CommonName {
  readonly language: string
  readonly name: string
}

export interface Taxonomy {
  readonly kingdom: string
  readonly phylum: string
  readonly className: string
  readonly order: string
  readonly family: string
  readonly genus: string
}

/** Mirrors `ErrorResponse` (Kotlin): `{ code, message }`. */
export interface ErrorResponse {
  readonly code: string
  readonly message: string
}

export type SearchResult =
  | { readonly ok: true; readonly species: readonly SpeciesResponse[] }
  | { readonly ok: false; readonly error: ErrorResponse }

const API_BASE = import.meta.env.VITE_API_BASE

/**
 * `GET /species/search?q=&limit=` → `SpeciesResponse[]`, or an `ErrorResponse` on 4xx/5xx
 * (e.g. `InvalidQuery` when the query is under 2 characters, matching the backend rule).
 */
export async function searchSpecies(
  query: string,
  limit = 20,
  abort?: AbortSignal,
): Promise<SearchResult> {
  if (API_BASE === undefined || API_BASE === '') {
    return mockSearchSpecies(query, limit)
  }
  const url = `${API_BASE}/species/search?q=${encodeURIComponent(query)}&limit=${String(limit)}`
  const res = await fetch(url, abort ? { signal: abort } : {})
  if (res.ok) {
    return { ok: true, species: (await res.json()) as SpeciesResponse[] }
  }
  const error = (await res.json().catch(() => ({
    code: 'ERROR',
    message: `Request failed (HTTP ${String(res.status)})`,
  }))) as ErrorResponse
  return { ok: false, error }
}
