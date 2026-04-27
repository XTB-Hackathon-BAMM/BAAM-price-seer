import { useRanking } from '../api/queries'
import { TEAM_NAME } from '../constants'
import { normalizeRanking } from '../api/normalize'

function isUs(team: string): boolean {
  return team.toLowerCase() === TEAM_NAME.toLowerCase()
}

export function LiveRanking() {
  const { data, isLoading, error } = useRanking()

  if (isLoading) return <div className="panel">Loading ranking…</div>
  if (error) return <div className="panel panel--error">Ranking error: {String(error)}</div>

  const sorted = normalizeRanking(data).sort((a, b) => b.accuracy - a.accuracy)

  return (
    <section className="panel">
      <h2 className="panel__title">Overall Ranking</h2>
      <table className="ranking">
        <thead>
          <tr>
            <th>#</th>
            <th>Team</th>
            <th>Acc</th>
            <th>Hits</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((entry, idx) => {
            const us = isUs(entry.team)
            const pos = entry.position ?? idx + 1
            const correct = entry.correctPredictions
            const total = entry.actualScoredPredictions
            return (
              <tr key={`${entry.team}-${idx}`} className={us ? 'ranking__row--us' : ''}>
                <td>{pos}</td>
                <td title={entry.team}>{entry.team}{us ? ' ★' : ''}</td>
                <td>{entry.accuracy.toFixed(1)}%</td>
                <td>
                  {correct ?? '—'}
                  {total != null ? ` / ${total}` : ''}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}