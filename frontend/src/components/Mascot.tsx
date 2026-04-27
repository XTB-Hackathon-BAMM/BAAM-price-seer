import { useEffect, useRef, useState } from 'react'
import { useHypeLevel } from '../hooks/useHypeLevel'
import { useTeamRanking } from '../api/queries'
import { normalizePredictions } from '../api/normalize'
import { useCommentary } from '../hooks/useCommentary'
import { useRandomWalk } from '../hooks/useRandomWalk'
import type { NarrationController } from '../hooks/useAudioCrossfade'
import { TEAM_NAME } from '../constants'

type Reaction = 'idle' | 'hit' | 'miss'

interface Props {
  narration: NarrationController
}

function useReactionFromLastPrediction(): Reaction {
  const { data } = useTeamRanking()
  const preds = normalizePredictions(data?.predictions)
  const last = preds[preds.length - 1]
  const lastTsRef = useRef<string | undefined>(undefined)
  const [reaction, setReaction] = useState<Reaction>('idle')

  useEffect(() => {
    if (!last) return
    if (lastTsRef.current === last.timestamp) return
    lastTsRef.current = last.timestamp
    setReaction(last.correct ? 'hit' : 'miss')
    const t = setTimeout(() => setReaction('idle'), 2500)
    return () => clearTimeout(t)
  }, [last])

  return reaction
}

export function Mascot({ narration }: Props) {
  const hype = useHypeLevel()
  const reaction = useReactionFromLastPrediction()
  const { position, isWalking, walkDurationMs } = useRandomWalk({
    intervalMs: 5600,
    walkDurationMs: 2400,
  })
  const line = useCommentary(6000)
  const lastSpokenLineRef = useRef<string | undefined>(undefined)
  const previousNarrationEnabledRef = useRef(narration.enabled)
  const [bubbleLine, setBubbleLine] = useState(line)
  const badge = reaction === 'hit'
    ? 'LOCKED IN'
    : reaction === 'miss'
      ? 'RECALC'
      : hype.level === 'max'
        ? 'OVERDRIVE'
        : hype.level === 'hype'
          ? 'SCANNING'
          : 'PATROL'
  const flairTokens = reaction === 'hit'
    ? ['STONKS', '+AURA']
    : reaction === 'miss'
      ? ['AUĆ', 'RESET']
      : hype.level === 'max'
        ? ['NO BRAKES', 'TO THE MOON']
        : hype.level === 'hype'
          ? ['LOCK IN', 'ALGO MODE']
          : ['BEEP', 'PATROL']

  useEffect(() => {
    if (!line || bubbleLine === line) return

    const timeoutId = window.setTimeout(() => {
      setBubbleLine(line)
    }, 140)

    return () => {
      window.clearTimeout(timeoutId)
    }
  }, [line, bubbleLine])

  useEffect(() => {
    if (!previousNarrationEnabledRef.current && narration.enabled) {
      lastSpokenLineRef.current = undefined
    }

    previousNarrationEnabledRef.current = narration.enabled
  }, [narration.enabled])

  useEffect(() => {
    if (isWalking || !line) return
    if (lastSpokenLineRef.current === line) return

    const emotion = reaction === 'hit'
      ? 'victory'
      : reaction === 'miss'
        ? 'concerned'
        : hype.level === 'max'
          ? 'excited'
          : hype.level === 'hype'
            ? 'focused'
            : 'calm'

    narration.speak(line, {
      emotion,
      volume: 1,
    })
    lastSpokenLineRef.current = line
  }, [narration, line, isWalking, reaction, hype.level])

  const cls = [
    'mascot',
    `mascot--${hype.level}`,
    `mascot--${reaction}`,
    isWalking ? 'mascot--walking' : 'mascot--idle',
    `mascot--facing-${position.facing}`,
  ].join(' ')
  const bubblePhase = line && bubbleLine !== line ? 'switching' : 'visible'

  return (
    <div
      className={cls}
      style={{
        left: position.x,
        top: position.y,
        transition: `left ${walkDurationMs}ms linear, top ${walkDurationMs}ms linear`,
      }}
      aria-hidden="true"
    >
      <div className="mascot__glow" />
      <div className="mascot__trail" />
      <div className="mascot__radar">
        <div className="mascot__radar-sweep" />
        <span className="mascot__radar-blip mascot__radar-blip--1" />
        <span className="mascot__radar-blip mascot__radar-blip--2" />
        <span className="mascot__radar-blip mascot__radar-blip--3" />
      </div>
      <div className="mascot__ring mascot__ring--1" />
      <div className="mascot__ring mascot__ring--2" />
      <span className="mascot__spark mascot__spark--1" />
      <span className="mascot__spark mascot__spark--2" />
      <span className="mascot__spark mascot__spark--3" />
      <div className="mascot__stickers">
        {flairTokens.map((token, index) => (
          <span key={`${token}-${index}`} className={`mascot__sticker mascot__sticker--${index + 1}`}>
            {token}
          </span>
        ))}
      </div>
      {bubbleLine && (
        <div key={bubbleLine} className={`mascot__bubble mascot__bubble--${bubblePhase}`}>
          <span>{bubbleLine}</span>
        </div>
      )}
      <div className="mascot__badge">{badge}</div>
      {reaction === 'hit' && <div className="mascot__pop">NICE!</div>}
      {reaction === 'miss' && <div className="mascot__pop mascot__pop--sad">RETRY</div>}

      <svg viewBox="0 0 160 190" className="mascot__svg">
        <defs>
          <linearGradient id="mascotSuit" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#fb7185" />
            <stop offset="55%" stopColor="#e11d48" />
            <stop offset="100%" stopColor="#9f1239" />
          </linearGradient>
          <linearGradient id="mascotVisor" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#dbeafe" />
            <stop offset="45%" stopColor="#7dd3fc" />
            <stop offset="100%" stopColor="#1d4ed8" />
          </linearGradient>
          <radialGradient id="mascotCore" cx="50%" cy="50%" r="60%">
            <stop offset="0%" stopColor="#fef08a" />
            <stop offset="50%" stopColor="#f97316" />
            <stop offset="100%" stopColor="#fb7185" />
          </radialGradient>
          <linearGradient id="mascotCape" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#7c3aed" />
            <stop offset="100%" stopColor="#312e81" />
          </linearGradient>
          <linearGradient id="mascotFlame" x1="0%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#fef08a" />
            <stop offset="45%" stopColor="#fb923c" />
            <stop offset="100%" stopColor="#ef4444" />
          </linearGradient>
        </defs>

        <ellipse cx="80" cy="176" rx="34" ry="8" fill="rgba(0,0,0,0.35)" />

        <g className="mascot__aura">
          <circle className="mascot__aura-ring mascot__aura-ring--outer" cx="80" cy="72" r="54" />
          <circle className="mascot__aura-ring mascot__aura-ring--inner" cx="80" cy="72" r="41" />
        </g>

        <path
          className="mascot__cape"
          d="M118 74 C138 94 136 142 110 156 L98 116 L92 84 Z"
          fill="url(#mascotCape)"
        />

        <g className="mascot__thrusters">
          <rect x="60" y="126" width="12" height="18" rx="5" fill="#111827" />
          <rect x="88" y="126" width="12" height="18" rx="5" fill="#111827" />
          <path className="mascot__flame mascot__flame--l" d="M66 143 C58 154 60 165 66 172 C72 165 74 154 66 143 Z" fill="url(#mascotFlame)" />
          <path className="mascot__flame mascot__flame--r" d="M94 143 C86 154 88 165 94 172 C100 165 102 154 94 143 Z" fill="url(#mascotFlame)" />
        </g>

        <g className="mascot__legs">
          <rect className="mascot__leg mascot__leg--l" x="62" y="126" width="12" height="28" rx="5" fill="#111827" />
          <rect className="mascot__leg mascot__leg--r" x="86" y="126" width="12" height="28" rx="5" fill="#111827" />
          <rect x="56" y="150" width="24" height="11" rx="5" fill="#1f2937" />
          <rect x="80" y="150" width="24" height="11" rx="5" fill="#1f2937" />
        </g>

        <g className="mascot__arm mascot__arm--left">
          <rect x="32" y="86" width="16" height="44" rx="8" fill="url(#mascotSuit)" />
          <circle cx="40" cy="132" r="7" fill="#fde68a" stroke="#111827" strokeWidth="2" />
        </g>
        <g className="mascot__arm mascot__arm--right">
          <rect x="112" y="86" width="16" height="44" rx="8" fill="url(#mascotSuit)" />
          <circle cx="120" cy="132" r="7" fill="#fde68a" stroke="#111827" strokeWidth="2" />
        </g>

        <g className="mascot__body">
          <rect x="48" y="70" width="64" height="64" rx="18" fill="url(#mascotSuit)" stroke="#7f1d1d" strokeWidth="2" />
          <rect x="60" y="82" width="40" height="10" rx="5" fill="rgba(255,255,255,0.18)" />
          <circle className="mascot__core" cx="80" cy="105" r="15" fill="url(#mascotCore)" />
          <circle cx="80" cy="105" r="22" fill="none" stroke="rgba(250,204,21,0.28)" strokeWidth="4" />
          <text
            x="80"
            y="110"
            textAnchor="middle"
            fontSize="13"
            fontWeight="900"
            fill="#0f172a"
            fontFamily="ui-monospace, Menlo, monospace"
            letterSpacing="1"
          >
            {TEAM_NAME.toUpperCase()}
          </text>
        </g>

        <g className="mascot__head">
          <path className="mascot__antenna" d="M80 22 L80 10" stroke="#111827" strokeWidth="4" strokeLinecap="round" />
          <circle className="mascot__antenna-tip" cx="80" cy="8" r="5" fill="#facc15" />
          <circle cx="80" cy="48" r="31" fill="#fde68a" stroke="#111827" strokeWidth="2.5" />
          <path d="M57 35 C69 25 91 25 103 35" fill="none" stroke="#111827" strokeWidth="3" strokeLinecap="round" />
          <g className="mascot__brows">
            <path className="mascot__brow mascot__brow--left" d="M62 40 Q69 36 76 39" fill="none" stroke="#111827" strokeWidth="3" strokeLinecap="round" />
            <path className="mascot__brow mascot__brow--right" d="M84 39 Q91 36 98 40" fill="none" stroke="#111827" strokeWidth="3" strokeLinecap="round" />
          </g>
          <path className="mascot__visor" d="M55 42 Q80 28 105 42 Q103 66 80 68 Q57 66 55 42 Z" fill="url(#mascotVisor)" stroke="#0f172a" strokeWidth="2" />
          <path className="mascot__visor-shine" d="M63 39 C72 33 80 32 90 35" fill="none" stroke="rgba(255,255,255,0.75)" strokeWidth="3" strokeLinecap="round" />
          <g className="mascot__eyes">
            <ellipse className="mascot__eye mascot__eye--left" cx="70" cy="50" rx="4.5" ry="6" fill="#0f172a" />
            <ellipse className="mascot__eye mascot__eye--right" cx="90" cy="50" rx="4.5" ry="6" fill="#0f172a" />
            <circle className="mascot__pupil mascot__pupil--left" cx="70" cy="50" r="1.8" fill="#67e8f9" />
            <circle className="mascot__pupil mascot__pupil--right" cx="90" cy="50" r="1.8" fill="#67e8f9" />
            <circle cx="71" cy="48" r="1.1" fill="#ffffff" />
            <circle cx="91" cy="48" r="1.1" fill="#ffffff" />
          </g>
          <g className="mascot__binoculars">
            <circle cx="70" cy="50" r="8" fill="#1f2937" stroke="#000" strokeWidth="1.5" />
            <circle cx="90" cy="50" r="8" fill="#1f2937" stroke="#000" strokeWidth="1.5" />
            <rect x="76" y="47" width="8" height="6" fill="#1f2937" />
          </g>
          <circle cx="62" cy="58" r="3" fill="rgba(244,114,182,0.35)" />
          <circle cx="98" cy="58" r="3" fill="rgba(244,114,182,0.35)" />
          <path
            className="mascot__mouth"
            d="M71 60 Q80 68 89 60"
            stroke="#111827"
            strokeWidth="3"
            fill="none"
            strokeLinecap="round"
          />
        </g>
      </svg>
    </div>
  )
}