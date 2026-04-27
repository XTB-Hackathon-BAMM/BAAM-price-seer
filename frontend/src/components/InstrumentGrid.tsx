import { useVisibleInstruments, useLatestPrices } from '../api/queries'
import { InstrumentCard } from './InstrumentCard'
import { normalizePrices } from '../api/normalize'

export function InstrumentGrid() {
  const { data: symbols = [], isLoading, error } = useVisibleInstruments()
  const { data: prices } = useLatestPrices()
  const bySymbol = normalizePrices(prices)

  if (isLoading) {
    return <section className="grid"><div className="panel">Loading instruments…</div></section>
  }

  if (error && symbols.length === 0) {
    return <section className="panel panel--error">Instrument error: {String(error)}</section>
  }

  if (symbols.length === 0) {
    return <section className="panel">No instruments available.</section>
  }

  return (
    <section className="grid">
      {symbols.map((symbol) => (
        <InstrumentCard key={symbol} symbol={symbol} price={bySymbol.get(symbol)} />
      ))}
    </section>
  )
}
