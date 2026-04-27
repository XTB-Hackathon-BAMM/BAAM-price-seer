import { apiGet } from './client'
import type {
  LatestPrice,
  RankingEntry,
  ServiceStatus,
  TeamRanking,
} from '../types/oracle'

const enc = encodeURIComponent

export const oracleApi = {
  instruments: (signal?: AbortSignal) => apiGet<string[]>('/api/instruments', signal),

  latestPrices: (signal?: AbortSignal) =>
    apiGet<LatestPrice[]>('/api/prices/latest', signal),

  ranking: (symbol?: string, signal?: AbortSignal) => {
    const path = symbol ? `/api/ranking?symbol=${enc(symbol)}` : '/api/ranking'
    return apiGet<RankingEntry[]>(path, signal)
  },

  teamRanking: (team: string, symbol?: string, signal?: AbortSignal) => {
    const base = `/api/ranking/${enc(team)}`
    const path = symbol ? `${base}?symbol=${enc(symbol)}` : base
    return apiGet<TeamRanking>(path, signal)
  },

  status: (signal?: AbortSignal) => apiGet<ServiceStatus>('/api/status', signal),
}
