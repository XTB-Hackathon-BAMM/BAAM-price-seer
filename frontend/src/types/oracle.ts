export type Direction = 'UP' | 'DOWN'

export interface RankingEntry {
  position?: number
  team: string
  accuracy: number
  correctPredictions?: number
  actualScoredPredictions?: number
  windowSize?: number
  lastPredictionAt?: string
}

export interface RankingResponse {
  updatedAt?: string
  windowSize?: number
  ranking: RankingEntry[]
}

export interface PredictionResult {
  timestamp: string
  symbol: string
  direction: Direction
  correct: boolean
  actualDirection?: Direction
  open?: number
  close?: number
}

export interface TeamRanking {
  team: string
  accuracy: number
  correctPredictions?: number
  actualScoredPredictions?: number
  windowSize?: number
  symbol?: string
  predictions?: PredictionResult[]
}

export interface LatestPrice {
  symbol: string
  timestamp: string
  interval?: string
  open: number
  high: number
  low: number
  close: number
}

export interface LatestPricesResponse {
  updatedAt?: string
  prices: LatestPrice[]
}

export interface ServiceStatus {
  status?: string
  uptime?: number
  currentMinute?: string
  [k: string]: unknown
}