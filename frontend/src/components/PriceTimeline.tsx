import { useEffect, useState } from 'react'
import { useLatestPrices, useTeamRanking, useVisibleInstruments } from '../api/queries'
import { normalizePredictions, normalizePrices } from '../api/normalize'
import type { LatestPrice } from '../types/oracle'
import { DEFAULT_TIMELINE_SYMBOL, TEAM_NAME } from '../constants'

interface PriceSample {
  observedAt: string
  timestamp: string
  close: number
}

function formatPrice(value?: number): string {
  if (value == null) return '—'
  return value.toFixed(value < 10 ? 4 : 2)
}

function formatSigned(value: number, digits: number): string {
  return `${value >= 0 ? '+' : ''}${value.toFixed(digits)}`
}

function shortTime(timestamp?: string): string {
  return timestamp ? timestamp.slice(11, 19) : '—'
}

function priceDelta(price?: LatestPrice): { delta: number; pct: number } | null {
  if (!price) return null
  const delta = price.close - price.open
  const pct = price.open !== 0 ? (delta / price.open) * 100 : 0
  return { delta, pct }
}

function trendDirection(value: number): 'up' | 'down' | 'flat' {
  if (value > 0) return 'up'
  if (value < 0) return 'down'
  return 'flat'
}

function appendSample(samples: PriceSample[] | undefined, price: LatestPrice, observedAt: string): PriceSample[] {
  const current = samples ?? []
  const last = current[current.length - 1]

  if (last && last.timestamp === price.timestamp && last.close === price.close) {
    return current
  }

  return [...current, { observedAt, timestamp: price.timestamp, close: price.close }].slice(-45)
}

export function PriceTimeline() {
  const { data: instruments = [], isLoading: isLoadingInstruments } = useVisibleInstruments()
  const { data: latestPrices, isLoading: isLoadingPrices, error: pricesError } = useLatestPrices()
  const [symbol, setSymbol] = useState<string>(DEFAULT_TIMELINE_SYMBOL)
  const [historyBySymbol, setHistoryBySymbol] = useState<Record<string, PriceSample[]>>({})
  const selectedSymbol = instruments.includes(symbol)
    ? symbol
    : (instruments.includes(DEFAULT_TIMELINE_SYMBOL) ? DEFAULT_TIMELINE_SYMBOL : instruments[0])
  const symbolQuery = useTeamRanking(selectedSymbol, undefined, Boolean(selectedSymbol))
  const overallQuery = useTeamRanking()
  const latestBySymbol = normalizePrices(latestPrices)

  useEffect(() => {
    if (latestBySymbol.size === 0) return

    const observedAt = new Date().toISOString()
    setHistoryBySymbol((previous) => {
      let changed = false
      const next = { ...previous }

      for (const [instrument, price] of latestBySymbol.entries()) {
        const appended = appendSample(next[instrument], price, observedAt)
        if (appended !== next[instrument]) {
          next[instrument] = appended
          changed = true
        }
      }

      return changed ? next : previous
    })
  }, [latestBySymbol])

  const symbolPredictions = normalizePredictions(symbolQuery.data?.predictions)
  const overallPredictions = normalizePredictions(overallQuery.data?.predictions).filter(
    (prediction) => prediction.symbol?.trim().toLowerCase() === selectedSymbol?.trim().toLowerCase(),
  )
  const predictions = (symbolPredictions.length > 0 ? symbolPredictions : overallPredictions)
    .slice()
    .sort((a, b) => a.timestamp.localeCompare(b.timestamp))
  const priceHistory = selectedSymbol ? (historyBySymbol[selectedSymbol] ?? []) : []
  const currentPrice = selectedSymbol ? latestBySymbol.get(selectedSymbol) : undefined
  const liveDelta = priceDelta(currentPrice)
  const recentSamples = priceHistory.slice(-6).reverse()
  const firstSample = priceHistory[0]
  const lastSample = priceHistory[priceHistory.length - 1]
  const trendDelta = firstSample && lastSample ? lastSample.close - firstSample.close : liveDelta?.delta ?? 0
  const trendPct = firstSample && firstSample.close !== 0
    ? (trendDelta / firstSample.close) * 100
    : (liveDelta?.pct ?? 0)
  const trend = trendDirection(liveDelta?.delta ?? trendDelta)
  const lastPrediction = predictions[predictions.length - 1]
  const predictionStatus = lastPrediction ? (lastPrediction.correct ? 'hit' : 'miss') : 'idle'
  const oracleAccuracy = symbolQuery.data?.accuracy ?? overallQuery.data?.accuracy
  const isLoading = symbolQuery.isLoading || overallQuery.isLoading || isLoadingPrices
  const errorMessage = symbolQuery.error ?? overallQuery.error
  const recentPredictions = predictions.slice(-4).reverse()
  const oracleMode = symbolPredictions.length > 0
    ? 'Instrument signal'
    : overallPredictions.length > 0
      ? 'Team signal fallback'
      : 'Waiting for Oracle signal'

  return (
    <section className="panel panel--wide pulse">
      <div className="panel__head">
        <h2 className="panel__title">Instrument Pulse · {TEAM_NAME}</h2>
        <select
          className="select"
          value={selectedSymbol ?? ''}
          disabled={instruments.length === 0}
          onChange={(e) => setSymbol(e.target.value)}
        >
          {instruments.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>
      <div className="pulse__sources">
        <div className="pulse__source-chip">Live feed active</div>
        <div className="pulse__source-chip">Oracle: {oracleMode}</div>
      </div>
      {isLoadingInstruments && <div className="muted">Loading instruments…</div>}
      {!isLoadingInstruments && !selectedSymbol && (
        <div className="muted">No instruments available.</div>
      )}
      {pricesError && !isLoadingPrices && (
        <div className="panel panel--error">Live price error: {String(pricesError)}</div>
      )}
      {selectedSymbol && errorMessage && !isLoading && (
        <div className="panel panel--error">Timeline error: {String(errorMessage)}</div>
      )}
      {selectedSymbol && isLoading && <div className="muted">Loading…</div>}
      {selectedSymbol && !isLoading && !errorMessage && !pricesError && (
        <>
          <div className={`pulse__hero pulse__hero--${trend}`}>
            <div>
              <div className="pulse__symbol">{selectedSymbol}</div>
              <div className="pulse__price">{formatPrice(currentPrice?.close)}</div>
              <div className={`pulse__change pulse__change--${trend}`}>
                {liveDelta
                  ? `${formatSigned(liveDelta.delta, currentPrice && currentPrice.close < 10 ? 4 : 2)} (${formatSigned(liveDelta.pct, 2)}%)`
                  : 'Awaiting live price snapshot'}
              </div>
            </div>

            <div className="pulse__oracle">
              <div className="pulse__label">Oracle signal</div>
              {lastPrediction ? (
                <div className="pulse__oracle-line">
                  <span className={`pred pred--${lastPrediction.direction.toLowerCase()}`}>{lastPrediction.direction}</span>
                  <span className={`pulse__oracle-result pulse__oracle-result--${predictionStatus}`}>
                    {lastPrediction.correct ? 'HIT' : 'MISS'}
                  </span>
                </div>
              ) : (
                <div className="muted">No prediction yet</div>
              )}
            </div>
          </div>

          <div className="pulse__metrics">
            <div className="pulse__metric">
              <span className="pulse__label">Short run trend</span>
              <strong className={`pulse__metric-value pulse__metric-value--${trendDirection(trendDelta)}`}>
                {formatSigned(trendDelta, currentPrice && currentPrice.close < 10 ? 4 : 2)}
              </strong>
              <span className="muted">{formatSigned(trendPct, 2)}% over {Math.max(priceHistory.length, 1)} snapshots</span>
            </div>

            <div className="pulse__metric">
              <span className="pulse__label">Oracle accuracy</span>
              <strong className="pulse__metric-value">{oracleAccuracy != null ? `${oracleAccuracy.toFixed(1)}%` : '—'}</strong>
              <span className="muted">{oracleMode}</span>
            </div>

            <div className="pulse__metric">
              <span className="pulse__label">Last price update</span>
              <strong className="pulse__metric-value">{shortTime(lastSample?.observedAt ?? currentPrice?.timestamp)}</strong>
              <span className="muted">Live market snapshot</span>
            </div>

            <div className="pulse__metric">
              <span className="pulse__label">Prediction state</span>
              <strong className={`pulse__metric-value pulse__metric-value--${predictionStatus === 'hit' ? 'up' : predictionStatus === 'miss' ? 'down' : 'flat'}`}>
                {lastPrediction ? lastPrediction.direction : 'WAITING'}
              </strong>
              <span className="muted">{predictions.length > 0 ? `${predictions.length} markers available` : 'No Oracle markers yet'}</span>
            </div>
          </div>

          <div className="pulse__grid">
            <div className="pulse__card">
              <div className="pulse__card-title">Recent tape</div>
              {recentSamples.length > 0 ? (
                <div className="pulse__tape">
                  {recentSamples.map((sample, index) => {
                    const older = recentSamples[index + 1]
                    const move = older ? sample.close - older.close : 0
                    const moveDirection = trendDirection(move)

                    return (
                      <div key={`${sample.observedAt}-${sample.timestamp}`} className="pulse__tape-row">
                        <span className="pulse__tape-time">{shortTime(sample.observedAt)}</span>
                        <span className="pulse__tape-price">{formatPrice(sample.close)}</span>
                        <span className={`pulse__tape-move pulse__tape-move--${moveDirection}`}>
                          {older ? formatSigned(move, sample.close < 10 ? 4 : 2) : 'latest'}
                        </span>
                      </div>
                    )
                  })}
                </div>
              ) : (
                <div className="muted">Waiting for snapshots from `/api/prices/latest`.</div>
              )}
            </div>

            <div className="pulse__card">
              <div className="pulse__card-title">Signal overview</div>
              <div className="pulse__feed">
                <div className="pulse__feed-item">
                  <span className="pulse__label">Live feed</span>
                  <span className="pulse__feed-path">Streaming market snapshot for the selected instrument</span>
                </div>
                <div className="pulse__feed-item">
                  <span className="pulse__label">Oracle</span>
                  <span className="pulse__feed-path">{oracleMode}</span>
                </div>
              </div>

              {recentPredictions.length > 0 ? (
                <div className="pulse__prediction-list">
                  {recentPredictions.map((prediction) => (
                    <div key={`${prediction.timestamp}-${prediction.direction}`} className="pulse__prediction-row">
                      <span>{shortTime(prediction.timestamp)}</span>
                      <span className={`pred pred--${prediction.direction.toLowerCase()}`}>{prediction.direction}</span>
                      <span className={`pulse__oracle-result pulse__oracle-result--${prediction.correct ? 'hit' : 'miss'}`}>
                        {prediction.correct ? '✓' : '✗'}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="muted">No predictions returned yet for this instrument. The panel still uses live prices so the view stays populated.</div>
              )}
            </div>
          </div>
        </>
      )}
    </section>
  )
}

