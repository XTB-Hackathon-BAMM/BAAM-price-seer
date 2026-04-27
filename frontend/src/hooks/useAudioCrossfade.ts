import { useCallback, useEffect, useRef, useState } from 'react'

type NarrationEmotion = 'calm' | 'focused' | 'excited' | 'concerned' | 'victory'

export interface NarrationOptions {
  pitch?: number
  rate?: number
  volume?: number
  emotion?: NarrationEmotion
}

export interface NarrationController {
  enabled: boolean
  supported: boolean
  toggle: () => Promise<void>
  speak: (text: string, options?: NarrationOptions) => void
}

type SpeechSynthesisWindow = Window & typeof globalThis & {
  speechSynthesis?: SpeechSynthesis
}

interface NarrationEngine {
  synth: SpeechSynthesis
  voices: SpeechSynthesisVoice[]
  refreshVoices: () => void
  dispose: () => void
}

interface SpeechUtteranceCtor {
  new (text?: string): SpeechSynthesisUtterance
}

type SpeechGlobal = typeof globalThis & {
  SpeechSynthesisUtterance?: SpeechUtteranceCtor
}

export function useAudioCrossfade(): NarrationController {
  const engineRef = useRef<NarrationEngine | null>(null)
  const [enabled, setEnabled] = useState(false)
  const supported = typeof window !== 'undefined' && Boolean(getSpeechSynthesis()) && Boolean(getSpeechUtteranceCtor())

  const ensureEngine = useCallback((): NarrationEngine | null => {
    if (engineRef.current) return engineRef.current

    const synth = getSpeechSynthesis()
    if (!synth) return null

    const engine: NarrationEngine = {
      synth,
      voices: [],
      refreshVoices: () => {
        engine.voices = synth.getVoices()
      },
      dispose: () => {
        synth.cancel()
      },
    }

    engine.refreshVoices()
    engineRef.current = engine
    return engine
  }, [])

  useEffect(() => {
    const engine = ensureEngine()
    if (!engine) return

    const handleVoicesChanged = () => engine.refreshVoices()
    engine.synth.addEventListener('voiceschanged', handleVoicesChanged)

    return () => {
      engine.synth.removeEventListener('voiceschanged', handleVoicesChanged)
      engineRef.current?.dispose()
      engineRef.current = null
    }
  }, [ensureEngine])

  const toggle = useCallback(async () => {
    if (!supported) return

    const engine = ensureEngine()
    if (!engine) return

    if (enabled) {
      engine.synth.cancel()
      setEnabled(false)
      return
    }

    engine.refreshVoices()
    setEnabled(true)
  }, [enabled, ensureEngine, supported])

  const speak = useCallback((text: string, options: NarrationOptions = {}) => {
    if (!enabled) return

    const engine = ensureEngine()
    const UtteranceCtor = getSpeechUtteranceCtor()
    if (!engine || !UtteranceCtor) return

    const trimmed = text.trim()
    if (trimmed.length === 0) return

    const utterance = new UtteranceCtor(applyProsody(trimmed, options.emotion))
    const voice = pickVoice(engine.voices)
    const emotion = options.emotion ?? 'focused'
    const defaults = emotionDefaults(emotion)

    if (voice) {
      utterance.voice = voice
      utterance.lang = voice.lang
    } else {
      utterance.lang = 'pl-PL'
    }

    utterance.pitch = clamp(options.pitch ?? defaults.pitch, 0.7, 1.8)
    utterance.rate = clamp(options.rate ?? defaults.rate, 0.75, 1.25)
    utterance.volume = clamp(options.volume ?? defaults.volume, 0.4, 1)

    engine.synth.cancel()
    engine.synth.speak(utterance)
  }, [enabled, ensureEngine])

  return { enabled, supported, toggle, speak }
}

function getSpeechSynthesis(): SpeechSynthesis | undefined {
  if (typeof window === 'undefined') return undefined
  const speechWindow = window as SpeechSynthesisWindow
  return speechWindow.speechSynthesis
}

function getSpeechUtteranceCtor(): SpeechUtteranceCtor | undefined {
  if (typeof globalThis === 'undefined') return undefined
  const speechGlobal = globalThis as SpeechGlobal
  return speechGlobal.SpeechSynthesisUtterance
}

function pickVoice(voices: SpeechSynthesisVoice[]): SpeechSynthesisVoice | undefined {
  const scored = voices
    .map((voice) => ({ voice, score: scoreVoice(voice) }))
    .sort((a, b) => b.score - a.score)

  return scored[0]?.voice
}

function scoreVoice(voice: SpeechSynthesisVoice): number {
  const name = voice.name.toLowerCase()
  const lang = voice.lang.toLowerCase()

  let score = 0

  if (lang.startsWith('pl')) score += 100
  else if (lang.startsWith('en')) score += 40

  if (voice.localService) score += 10

  for (const token of ['siri', 'google', 'premium', 'enhanced', 'natural', 'neural']) {
    if (name.includes(token)) score += 18
  }

  for (const token of ['compact', 'classic']) {
    if (name.includes(token)) score -= 8
  }

  return score
}

function emotionDefaults(emotion: NarrationEmotion): Required<Pick<NarrationOptions, 'pitch' | 'rate' | 'volume'>> {
  switch (emotion) {
    case 'calm':
      return { pitch: 0.96, rate: 0.9, volume: 0.92 }
    case 'focused':
      return { pitch: 1.01, rate: 0.97, volume: 0.96 }
    case 'excited':
      return { pitch: 1.16, rate: 1.08, volume: 1 }
    case 'concerned':
      return { pitch: 0.9, rate: 0.88, volume: 0.95 }
    case 'victory':
      return { pitch: 1.22, rate: 1.02, volume: 1 }
  }
}

function applyProsody(text: string, emotion: NarrationEmotion | undefined): string {
  const trimmed = text.trim()

  switch (emotion) {
    case 'calm':
      return `${trimmed}...`
    case 'excited':
      return trimmed.endsWith('!') ? trimmed : `${trimmed}!`
    case 'concerned':
      return trimmed.endsWith('...') ? trimmed : `${trimmed}...`
    case 'victory':
      return trimmed.endsWith('!') ? trimmed : `${trimmed}!`
    default:
      return trimmed
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}
