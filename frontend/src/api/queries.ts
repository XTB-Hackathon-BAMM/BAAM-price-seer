import { useQuery } from '@tanstack/react-query'
import { oracleApi } from './oracle'
import { POLL_INTERVAL_MS, TEAM_NAME } from '../constants'
import { normalizePrices } from './normalize'

function normalizeSymbols(symbols: string[]): string[] {
  return Array.from(
    new Set(
      symbols
        .filter((symbol): symbol is string => typeof symbol === 'string' && symbol.trim().length > 0)
        .map((symbol) => symbol.trim()),
    ),
  )
}

export function useRanking(symbol?: string) {
  return useQuery({
    queryKey: ['ranking', symbol ?? 'overall'],
    queryFn: ({ signal }) => oracleApi.ranking(symbol, signal),
    refetchInterval: POLL_INTERVAL_MS.ranking,
  })
}

export function useTeamRanking(
  symbol?: string,
  team?: string,
  enabled: boolean = true,
) {
  const resolvedTeam = team ?? TEAM_NAME

  return useQuery({
    queryKey: ['teamRanking', resolvedTeam, symbol ?? 'overall'],
    queryFn: ({ signal }) => oracleApi.teamRanking(resolvedTeam, symbol, signal),
    enabled,
    refetchInterval: POLL_INTERVAL_MS.teamRanking,
  })
}

export function useLatestPrices() {
  return useQuery({
    queryKey: ['prices', 'latest'],
    queryFn: ({ signal }) => oracleApi.latestPrices(signal),
    refetchInterval: POLL_INTERVAL_MS.prices,
  })
}

export function useInstruments() {
  return useQuery({
    queryKey: ['instruments'],
    queryFn: ({ signal }) => oracleApi.instruments(signal),
    select: (symbols) => normalizeSymbols(symbols),
    placeholderData: (previousData) => previousData,
    refetchInterval: POLL_INTERVAL_MS.status,
  })
}

export function useVisibleInstruments() {
  const instrumentsQuery = useInstruments()
  const pricesQuery = useLatestPrices()
  const priceSymbols = normalizeSymbols(Array.from(normalizePrices(pricesQuery.data).keys()))
  const data = instrumentsQuery.data && instrumentsQuery.data.length > 0
    ? instrumentsQuery.data
    : priceSymbols

  return {
    ...instrumentsQuery,
    data,
    isLoading: instrumentsQuery.isLoading && priceSymbols.length === 0,
    error: instrumentsQuery.error ?? pricesQuery.error,
  }
}

export function useServiceStatus() {
  return useQuery({
    queryKey: ['status'],
    queryFn: ({ signal }) => oracleApi.status(signal),
    refetchInterval: POLL_INTERVAL_MS.status,
  })
}
