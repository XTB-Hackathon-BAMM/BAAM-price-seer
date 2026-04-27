import { useTeamRanking } from '../api/queries'
import { normalizePredictions } from '../api/normalize'
import type { LatestPrice, PredictionResult } from '../types/oracle'

interface Props {
  symbol: string
  price?: LatestPrice
}

function formatAccuracy(accuracy?: number): string {
  if (accuracy == null) return '—'
  return `${accuracy.toFixed(1)}%`
}

function lastPrediction(preds: PredictionResult[]): PredictionResult | undefined {
  if (preds.length === 0) return undefined
  return preds[preds.length - 1]
}

function priceDelta(price?: LatestPrice): { delta: number; pct: number } | null {
  if (!price) return null
  const delta = price.close - price.open
  const pct = price.open !== 0 ? (delta / price.open) * 100 : 0
  return { delta, pct }
}

export function InstrumentCard({ symbol, price }: Props) {
  const { data: team } = useTeamRanking(symbol)
  const last = lastPrediction(normalizePredictions(team?.predictions))
  const delta = priceDelta(price)
  const dir = delta == null ? 'flat' : delta.delta > 0 ? 'up' : delta.delta < 0 ? 'down' : 'flat'

  return (
    <div className={`card card--${dir}`}>
      <div className="card__head">
        <span className="card__symbol">{symbol}</span>
        <span className="card__accuracy">{formatAccuracy(team?.accuracy)}</span>
      </div>
      <div className="card__price">
        {price ? price.close.toFixed(price.close < 10 ? 4 : 2) : '—'}
      </div>
      <div className="card__delta">
        {delta
          ? `${delta.delta >= 0 ? '+' : ''}${delta.delta.toFixed(4)} (${delta.pct.toFixed(2)}%)`
          : 'no price'}
      </div>
      <div className="card__pred">
        {last ? (
          <>
            <span className={`pred pred--${last.direction.toLowerCase()}`}>{last.direction}</span>
            <span className={`pred__result ${last.correct ? 'ok' : 'miss'}`}>
              {last.correct ? '✓' : '✗'}
            </span>
          </>
        ) : (
          <span className="muted">no prediction yet</span>
        )}
      </div>
    </div>
  )
}
