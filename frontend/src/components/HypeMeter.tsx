import { useHypeLevel } from '../hooks/useHypeLevel'
import type { NarrationController } from '../hooks/useAudioCrossfade'

interface Props {
  narration: NarrationController
}

export function HypeMeter({ narration }: Props) {
  const hype = useHypeLevel()

  const pct = Math.round(hype.intensity * 100)

  return (
    <section className={`panel hype hype--${hype.level}`}>
      <div className="panel__head">
        <h2 className="panel__title">Hype Meter</h2>
        <button
          type="button"
          className="audio-btn"
          disabled={!narration.supported}
          onClick={() => {
            void narration.toggle()
          }}
        >
          {!narration.supported ? 'VOICE N/A' : narration.enabled ? '🗣️ ON' : '🗣️ OFF'}
        </button>
      </div>
      <div className="hype__level">{hype.level.toUpperCase()}</div>
      <div className="hype__bar">
        <div className="hype__bar-fill" style={{ width: `${pct}%` }} />
      </div>
      <div className="hype__stats">
        <span>Streak: {hype.streak}</span>
        <span>Last 10: {(hype.accuracy * 100).toFixed(0)}%</span>
        <span>Intensity: {pct}%</span>
      </div>
    </section>
  )
}
