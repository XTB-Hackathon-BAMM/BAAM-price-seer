import { useEffect, useState } from 'react'

export interface Position {
  x: number
  y: number
  facing: 'left' | 'right'
}

const PADDING = 80
const RESERVED_BOTTOM = 40
const MASCOT_W = 170
const MASCOT_H = 210

function randomPosition(prev?: Position): Position {
  const maxX = Math.max(PADDING, window.innerWidth - MASCOT_W - PADDING)
  const maxY = Math.max(PADDING, window.innerHeight - MASCOT_H - RESERVED_BOTTOM)
  const x = PADDING + Math.random() * (maxX - PADDING)
  const y = PADDING + Math.random() * (maxY - PADDING)
  const facing: 'left' | 'right' = prev ? (x < prev.x ? 'left' : 'right') : 'right'
  return { x, y, facing }
}

export function useRandomWalk(opts: {
  intervalMs?: number
  walkDurationMs?: number
}) {
  const intervalMs = opts.intervalMs ?? 7000
  const walkDurationMs = opts.walkDurationMs ?? 3500

  const [position, setPosition] = useState<Position>(() => ({
    x: Math.max(PADDING, window.innerWidth - MASCOT_W - 40),
    y: Math.max(PADDING, window.innerHeight - MASCOT_H - 80),
    facing: 'left',
  }))
  const [isWalking, setIsWalking] = useState(false)

  useEffect(() => {
    let walkTimeout: number | undefined
    const id = setInterval(() => {
      setPosition((prev) => {
        const next = randomPosition(prev)
        return next
      })
      setIsWalking(true)
      window.clearTimeout(walkTimeout)
      walkTimeout = window.setTimeout(() => setIsWalking(false), walkDurationMs)
    }, intervalMs)
    return () => {
      clearInterval(id)
      window.clearTimeout(walkTimeout)
    }
  }, [intervalMs, walkDurationMs])

  useEffect(() => {
    const handler = () => setPosition((p) => randomPosition(p))
    window.addEventListener('resize', handler)
    return () => window.removeEventListener('resize', handler)
  }, [])

  return { position, isWalking, walkDurationMs }
}