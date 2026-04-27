import type { LatestPrice, PredictionResult, RankingEntry } from '../types/oracle'

const loggedShapes = new Set<string>()
function logShapeOnce(label: string, value: unknown) {
  if (loggedShapes.has(label)) return
  loggedShapes.add(label)
  // eslint-disable-next-line no-console
  console.warn(`[normalize] unexpected shape for ${label}:`, value)
}

function asArray<T>(value: unknown, label: string): T[] {
  if (Array.isArray(value)) return value as T[]
  if (value == null) return []
  if (typeof value === 'object') {
    const obj = value as Record<string, unknown>
    if (Array.isArray(obj.data)) return obj.data as T[]
    if (Array.isArray(obj.items)) return obj.items as T[]
    if (Array.isArray(obj.prices)) return obj.prices as T[]
    if (Array.isArray(obj.results)) return obj.results as T[]
    if (Array.isArray(obj.ranking)) return obj.ranking as T[]
    if (Array.isArray(obj.predictions)) return obj.predictions as T[]
    const values = Object.values(obj)
    if (values.length > 0 && values.every((v) => v && typeof v === 'object')) {
      logShapeOnce(label, value)
      return values as T[]
    }
  }
  logShapeOnce(label, value)
  return []
}

export function normalizePrices(value: unknown): Map<string, LatestPrice> {
  const arr = asArray<Partial<LatestPrice> & { symbol?: string }>(value, 'prices')
  const out = new Map<string, LatestPrice>()

  if (arr.length > 0) {
    for (const p of arr) {
      if (p && typeof p === 'object' && p.symbol) {
        out.set(p.symbol, p as LatestPrice)
      }
    }
    return out
  }
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    for (const [key, v] of Object.entries(value as Record<string, unknown>)) {
      if (v && typeof v === 'object') {
        const p = v as Partial<LatestPrice>
        out.set(p.symbol ?? key, { ...(p as LatestPrice), symbol: p.symbol ?? key })
      }
    }
  }
  return out
}

export function normalizeRanking(value: unknown): RankingEntry[] {
  const arr = asArray<RankingEntry>(value, 'ranking')
  if (arr.length > 0) return arr
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return Object.entries(value as Record<string, unknown>).map(([team, v]) => {
      if (v && typeof v === 'object') {
        const e = v as Partial<RankingEntry>
        return { team: e.team ?? team, accuracy: e.accuracy ?? 0, ...e }
      }
      return { team, accuracy: typeof v === 'number' ? v : 0 }
    })
  }
  return []
}

export function normalizePredictions(value: unknown): PredictionResult[] {
  return asArray<PredictionResult>(value, 'predictions')
}