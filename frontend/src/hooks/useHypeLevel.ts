import { useTeamRanking } from '../api/queries'
import { normalizePredictions } from '../api/normalize'
import type { PredictionResult } from '../types/oracle'

export type HypeLevel = 'chill' | 'hype' | 'max'

const RECENT_WINDOW = 10

function recentStreak(preds: PredictionResult[]): number {
  const recent = preds.slice(-RECENT_WINDOW)
  if (recent.length === 0) return 0
  let streak = 0
  for (let i = recent.length - 1; i >= 0; i--) {
    if (recent[i].correct) streak++
    else break
  }
  return streak
}

function recentAccuracy(preds: PredictionResult[]): number {
  const recent = preds.slice(-RECENT_WINDOW)
  if (recent.length === 0) return 0
  const hits = recent.filter((p) => p.correct).length
  return hits / recent.length
}

export interface HypeState {
  level: HypeLevel
  streak: number
  accuracy: number
  intensity: number
}

export function useHypeLevel(): HypeState {
  const { data } = useTeamRanking()
  const preds = normalizePredictions(data?.predictions)
  const streak = recentStreak(preds)

  const fromPreds = recentAccuracy(preds)
  const fromOverall = (data?.accuracy ?? 0) / 100
  const accuracy = preds.length > 0 ? fromPreds : fromOverall

  const intensity = Math.min(1, accuracy * 0.6 + Math.min(streak, 5) * 0.08)

  let level: HypeLevel = 'chill'
  if (intensity >= 0.75 || streak >= 4) level = 'max'
  else if (intensity >= 0.45 || streak >= 2) level = 'hype'

  return { level, streak, accuracy, intensity }
}