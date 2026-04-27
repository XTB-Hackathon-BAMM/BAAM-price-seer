import { useAudioCrossfade } from './hooks/useAudioCrossfade'
import { InstrumentGrid } from './components/InstrumentGrid'
import { LiveRanking } from './components/LiveRanking'
import { PriceTimeline } from './components/PriceTimeline'
import { HypeMeter } from './components/HypeMeter'
import { Mascot } from './components/Mascot'
import { useServiceStatus } from './api/queries'
import { TEAM_NAME } from './constants'

function StatusBar() {
  const { data, error, isLoading } = useServiceStatus()
  const status = isLoading ? 'connecting' : error ? 'unreachable' : 'connected'
  return (
    <div
      className={`statusbar ${status === 'connected' ? 'statusbar--ok' : status === 'unreachable' ? 'statusbar--err' : ''}`}
    >
      <span className="statusbar__brand">{TEAM_NAME} · Price Seer</span>
      <span className="statusbar__status">
        Oracle: {status}
        {data?.currentMinute ? ` · ${String(data.currentMinute)}` : ''}
      </span>
    </div>
  )
}

export default function App() {
  const narration = useAudioCrossfade()

  return (
    <div className="app">
      <StatusBar />
      <main className="app__main">
        <div className="app__col app__col--main">
          <InstrumentGrid />
          <PriceTimeline />
        </div>
        <div className="app__col app__col--side">
          <HypeMeter narration={narration} />
          <LiveRanking />
        </div>
      </main>
      <Mascot narration={narration} />
    </div>
  )
}
