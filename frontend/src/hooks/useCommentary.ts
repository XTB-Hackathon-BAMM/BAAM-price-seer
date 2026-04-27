import { useEffect, useRef, useState } from 'react'
import { useMemo } from 'react'
import { useHypeLevel } from './useHypeLevel'
import { useTeamRanking, useRanking } from '../api/queries'
import { normalizePredictions, normalizeRanking } from '../api/normalize'
import { TEAM_NAME } from '../constants'
import type { RankingEntry } from '../types/oracle'

const LINES_GENERAL = [
  `${TEAM_NAME} ma plan, reszta ma stres.`,
  `${TEAM_NAME} nie śpi, ${TEAM_NAME} liczy przewagę.`,
  'Predykcje są jak pizza — najlepsze gdy świeże i nasze.',
  'Spokój. Strategia. Profit. W tej kolejności.',
  `${TEAM_NAME} patrzy na tabelę jak właściciel na swoje udziały.`,
  'Liczby nie kłamią. Na szczęście dziś mówią po naszemu.',
  'Idę sprawdzić, kto dziś goni Bamm.',
  'Hmm... rywale znowu udają, że mają plan.',
  `${TEAM_NAME} robi swoje, a tabela powoli to rozumie.`,
  'Jedni zgadują, my wolimy liczyć.',
  `${TEAM_NAME} nie szuka wymówek, tylko kolejnych punktów.`,
  'Na rynku szum, u nas plan lotu.',
  `${TEAM_NAME} czyta wykresy jak poranną prasę.`,
  'Nie obiecujemy cudów. Dostarczamy liczby.',
  `${TEAM_NAME} wchodzi w kolejną minutę bez mrugnięcia.`,
  'Tabela żyje własnym życiem, ale i tak patrzy na nas.',
]

const LINES_CHILL = [
  'Cisza przed burzą, a Bamm liczy pioruny.',
  'Ładujemy energię, rywale mogą ładować wymówki.',
  'Spokojnie, Bamm lubi startować z chłodną głową.',
  'Każdy mistrz zaczynał od zera. My już dawno jesteśmy dalej.',
  'Minuta ciszy dla tych, którzy nie wierzą w comeback Bamm.',
  `${TEAM_NAME} zbiera dane, zanim zbierze punkty.`,
  'Jeszcze bez fajerwerków, ale silnik już mruczy.',
  `${TEAM_NAME} spokojnie ustawia celownik.`,
  'Bez pośpiechu. Dobre ruchy i tak robią największy hałas.',
  'To ten moment, gdy rynek myśli, że ma spokój.',
  `${TEAM_NAME} rozgrzewa model, nie ego.`,
]

const LINES_HYPE = [
  'Coś się dzieje! 👀',
  `${TEAM_NAME} łapie wiatr w żagle.`,
  'Czuję momentum po naszej stronie.',
  'Algorytm wchodzi w rytm, tabela nie nadąża.',
  `${TEAM_NAME} właśnie podkręca tempo.`,
  'Okej, teraz zaczyna się właściwa zabawa.',
  `${TEAM_NAME} przestaje pukać do czołówki i zaczyna wchodzić bez zaproszenia.`,
  'Widzę ruch, widzę tempo, widzę kłopoty dla rywali.',
  `${TEAM_NAME} podnosi ciśnienie i nie zamierza zwalniać.`,
  'Tego już nie da się nazwać przypadkiem.',
  `${TEAM_NAME} ma flow, a tabela robi się nerwowa.`,
]

const LINES_MAX = [
  `${TEAM_NAME.toUpperCase()} NA OGNIU 🔥`,
  'Niezatrzymywalni!',
  'Predict and conquer.',
  'Maklerzy płaczą, Bamm robi victory lap.',
  'Ten streak zasługuje na pomnik i sponsorów.',
  `${TEAM_NAME} jedzie bez hamulców i z pełnym zasięgiem.`,
  'To już nie momentum. To przejęcie sterów.',
  `${TEAM_NAME} świeci tak mocno, że leaderboard mruży oczy.`,
  'Pełna moc, zero litości, same konkrety.',
  `${TEAM_NAME} odpalił tryb, którego nie ma w instrukcji.`,
  'Jeśli ktoś szuka lidera dnia, właśnie go znalazł.',
]

const LINES_HIT = [
  'Bullseye! 🎯',
  'Trafione w 10!',
  'Mówiłem, że Bamm to dowiezie.',
  'Algorithm shenanigans, sponsored by Bamm.',
  `${TEAM_NAME} dopisuje kolejny konkret do bilansu.`,
  'To się nazywa wejść i od razu mieć rację.',
  `${TEAM_NAME} trafia czysto, bez rykoszetów.`,
  'Rynek powiedział jedno, my przeczytaliśmy to szybciej.',
  `${TEAM_NAME} zamyka tę akcję z plusem i klasą.`,
  'W punkt. Dokładnie tak to miało wyglądać.',
]

const LINES_MISS = [
  'Auć. Nikt nie jest doskonały.',
  'Rynek przez chwilę zapomniał, kto tu rządzi.',
  'To była zasadzka. Przyjmujemy, zapisujemy, jedziemy dalej.',
  'Nawet Oracle czasem mrugnie. Bamm nie panikuje.',
  `${TEAM_NAME} bierze to na klatę i wraca do liczb.`,
  'Jedna rysa nie zmienia całego obrazu.',
  `${TEAM_NAME} zaznacza błąd, poprawia kurs i jedzie dalej.`,
  'Tego punktu nie ma, ale plan nadal żyje.',
  `${TEAM_NAME} już widzi, gdzie rynek zrobił psikusa.`,
  'Bez dramatu. Jedna pomyłka to tylko koszt nauki.',
]

const LINES_LAST_PLACE = [
  'Ostatni dziś, pierwszy jutro.',
  'Comeback story incoming.',
  'Wszystko w grze, a Bamm lubi dramaturgię.',
  `${TEAM_NAME} zna drogę z dołu na górę.`,
  'To jeszcze nie koniec tabeli, tylko początek narracji.',
  `${TEAM_NAME} już nieraz zaczynał z tyłu i kończył z przodu.`,
  'Jeśli ma być comeback, to czemu nie od teraz?',
  `${TEAM_NAME} nie zamierza zostać tu ani minuty dłużej, niż trzeba.`,
]

const LINES_LEADING = [
  'CZUJESZ TĘ MOC?',
  'Top of the leaderboard, baby.',
  'Inni nas gonią, ale buty mają za ciężkie.',
  `${TEAM_NAME} prowadzi i nawet się nie ogląda.`,
  `${TEAM_NAME} siedzi na szczycie i dobrze mu z tym.`,
  'Pierwsze miejsce wygląda dziś wyjątkowo znajomo.',
  `${TEAM_NAME} ustawia tempo, reszta tylko próbuje nadążyć.`,
  'Lider jest jeden, a dziś mówi naszym głosem.',
  `${TEAM_NAME} prowadzi bez zbędnego hałasu, ale za to skutecznie.`,
]

type LineFactory = (ctx: CommentContext) => string

type LineCategory =
  | 'general'
  | 'chill'
  | 'hype'
  | 'max'
  | 'hit'
  | 'miss'
  | 'last_place'
  | 'leading'
  | 'moved_up'
  | 'moved_down'
  | 'chasing_leader'
  | 'rival_ahead'
  | 'rival_behind'
  | 'hit_rival'
  | 'miss_rival'
  | 'accuracy'

interface CommentSelection {
  line: string
  category: LineCategory
}

const LINES_MOVED_UP: LineFactory[] = [
  (ctx) => `${TEAM_NAME} wskakuje na P${ctx.position}. Proszę zrobić przejście.`,
  (ctx) => `Awans na P${ctx.position}. Tabela właśnie poczuła ciężar Bamm.`,
  (ctx) => `${TEAM_NAME} pnie się w górę. P${ctx.position} wygląda bardzo dobrze.`,
  (ctx) => `Skok na P${ctx.position}. ${TEAM_NAME} właśnie przypomniał o swojej obecności.`,
  (ctx) => `${TEAM_NAME} awansuje na P${ctx.position} i to nie jest ostatnie słowo.`,
  (ctx) => `Jest progres. P${ctx.position} już należy do ${TEAM_NAME}.`,
]

const LINES_MOVED_DOWN: LineFactory[] = [
  (ctx) => `Drobny poślizg na P${ctx.position}, ale Bamm zaraz to odkręci.`,
  () => `Spadek na chwilę. Spokojnie, Bamm nie zostawia takich rzeczy bez odpowiedzi.`,
  () => `Tabela się zachwiała. Zaraz ustawimy ją z powrotem pod Bamm.`,
  (ctx) => `P${ctx.position} to tylko przystanek. ${TEAM_NAME} już szykuje ruch powrotny.`,
  () => `${TEAM_NAME} cofnął się o krok, żeby za chwilę ruszyć dwa do przodu.`,
  () => 'Krótki zjazd, ale kierunek dalej jest ustawiony na comeback.',
]

const LINES_CHASING_LEADER: LineFactory[] = [
  (ctx) => `${teamLabel(ctx.leaderTeam)} jeszcze prowadzi, ale ${TEAM_NAME} ma już ich na radarze.`,
  (ctx) => `Liderem jest ${teamLabel(ctx.leaderTeam)}. Jeszcze.`,
  (ctx) => `${TEAM_NAME} patrzy na ${teamLabel(ctx.leaderTeam)} jak na następny checkpoint.`,
  (ctx) => `${teamLabel(ctx.leaderTeam)} jest przed nami tylko na papierze.`,
  (ctx) => `${TEAM_NAME} skraca dystans do ${teamLabel(ctx.leaderTeam)} z każdą sensowną decyzją.`,
  (ctx) => `Szczyt należy dziś do ${teamLabel(ctx.leaderTeam)}, ale ${TEAM_NAME} już puka do drzwi.`,
]

const LINES_RIVAL_AHEAD: LineFactory[] = [
  (ctx) => `${teamLabel(ctx.rivalAhead)} jest tuż przed nami. To bardzo tymczasowy stan.`,
  (ctx) => `Widzę ${teamLabel(ctx.rivalAhead)} przed maską. Zaraz ich miniemy.`,
  (ctx) => `${TEAM_NAME} ma dziś jeden cel: przeskoczyć ${teamLabel(ctx.rivalAhead)}.`,
  (ctx) => `${teamLabel(ctx.rivalAhead)} jest o krok przed nami, ale ten krok wygląda na ostatni.`,
  (ctx) => `${TEAM_NAME} już mierzy dystans do ${teamLabel(ctx.rivalAhead)}.`,
  (ctx) => `Przed nami ${teamLabel(ctx.rivalAhead)}. Niewygodna, ale krótkotrwała sytuacja.`,
]

const LINES_RIVAL_BEHIND: LineFactory[] = [
  (ctx) => `${teamLabel(ctx.rivalBehind)} siedzi nam na ogonie, ale Bamm nie oddaje pola.`,
  (ctx) => `Słyszę kroki ${teamLabel(ctx.rivalBehind)} za plecami. To dobrze, niech patrzą.`,
  (ctx) => `${teamLabel(ctx.rivalBehind)} goni, a ${TEAM_NAME} podkręca tempo.`,
  (ctx) => `${teamLabel(ctx.rivalBehind)} jest blisko, ale ${TEAM_NAME} nie otwiera drzwi za darmo.`,
  (ctx) => `${TEAM_NAME} czuje presję od ${teamLabel(ctx.rivalBehind)} i odpowiada przyspieszeniem.`,
  (ctx) => `${teamLabel(ctx.rivalBehind)} może patrzeć na plecy ${TEAM_NAME}, nic więcej.`,
]

const LINES_HIT_RIVAL: LineFactory[] = [
  (ctx) => `Piękny hit. ${teamLabel(ctx.rivalAhead)} może już czuć oddech ${TEAM_NAME}.`,
  () => `Trafione. Tabela właśnie zrobiła miejsce dla Bamm.`,
  () => `${TEAM_NAME} trafia, rywale liczą straty wizerunkowe.`,
  (ctx) => `Celnie. ${teamLabel(ctx.rivalAhead)} właśnie dostał sygnał ostrzegawczy.`,
  () => `${TEAM_NAME} dopina hit i od razu robi się ciaśniej w tabeli.`,
  () => 'Takie trafienia zmieniają atmosferę po obu stronach rankingu.',
]

const LINES_MISS_RIVAL: LineFactory[] = [
  (ctx) => `Jedna pomyłka i ${teamLabel(ctx.rivalBehind)} od razu się budzi. Bez paniki.`,
  () => `To był miss, ale ${TEAM_NAME} nie rozdaje punktów za darmo.`,
  (ctx) => `${teamLabel(ctx.rivalBehind)} pewnie się cieszy. Niech korzystają z tej chwili.`,
  (ctx) => `Miss. ${teamLabel(ctx.rivalBehind)} od razu poczuł szansę, ale to jeszcze nic nie znaczy.`,
  () => `${TEAM_NAME} odhacza pomyłkę i wraca do gry bez zbędnych emocji.`,
  () => 'Taki moment boli chwilę, ale dobrze resetuje koncentrację.',
]

const LINES_ACCURACY: LineFactory[] = [
  (ctx) => `${TEAM_NAME} trzyma skuteczność na poziomie ${ctx.accuracy?.toFixed(1)}%. Szanuj liczby.`,
  (ctx) => `${ctx.accuracy?.toFixed(1)}% skuteczności. ${TEAM_NAME} nie zgaduje, tylko wylicza.`,
  (ctx) => `${TEAM_NAME} dowozi ${ctx.accuracy?.toFixed(1)}% i dalej szuka zapasu.`,
  () => `${TEAM_NAME} utrzymuje skuteczność na wysokim poziomie.`,
]

function pick<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)]
}

function unique<T>(arr: T[]): T[] {
  return Array.from(new Set(arr))
}

function pickFresh(lines: string[], recentLines: string[]): string {
  const candidates = unique(lines)
  const freshCandidates = candidates.filter((line) => !recentLines.includes(line))
  return pick(freshCandidates.length > 0 ? freshCandidates : candidates)
}

function pickFromLines(arr: string[], recentLines: string[]): string {
  return pickFresh(arr, recentLines)
}

function pickFromFactories(arr: LineFactory[], ctx: CommentContext, recentLines: string[]): string {
  return pickFresh(arr.map((factory) => factory(ctx)), recentLines)
}

function choosePreferredSelection(
  selections: CommentSelection[],
  recentCategories: LineCategory[],
): CommentSelection {
  const freshSelection = selections.find((selection) => !recentCategories.includes(selection.category))
  if (freshSelection) return freshSelection

  const differentSelection = selections.find((selection) => selection.category !== recentCategories[0])
  return differentSelection ?? selections[0]
}

function teamLabel(team?: string): string {
  return team ?? 'liderzy'
}

interface CommentContext {
  level: 'chill' | 'hype' | 'max'
  lastHit?: boolean
  position?: number
  previousPosition?: number
  totalTeams?: number
  leaderTeam?: string
  rivalAhead?: string
  rivalBehind?: string
  accuracy?: number
}

interface RankedEntry extends RankingEntry {
  resolvedPosition: number
}

function withResolvedPositions(entries: RankingEntry[]): RankedEntry[] {
  return entries.map((entry, index) => ({
    ...entry,
    resolvedPosition: entry.position ?? index + 1,
  }))
}

function findTableContext(entries: RankedEntry[]) {
  const index = entries.findIndex((entry) => entry.team.toLowerCase() === TEAM_NAME.toLowerCase())
  if (index === -1) {
    return {
      us: undefined,
      leader: entries[0],
      rivalAhead: undefined,
      rivalBehind: undefined,
    }
  }

  return {
    us: entries[index],
    leader: entries[0],
    rivalAhead: entries[index - 1],
    rivalBehind: entries[index + 1],
  }
}

function chooseLine(
  ctx: CommentContext,
  recentLines: string[] = [],
  recentCategories: LineCategory[] = [],
): CommentSelection {
  const r = Math.random()
  const selections: CommentSelection[] = []

  if (ctx.position && ctx.previousPosition && ctx.position < ctx.previousPosition) {
    return {
      category: 'moved_up',
      line: pickFromFactories(LINES_MOVED_UP, ctx, recentLines),
    }
  }

  if (ctx.position && ctx.previousPosition && ctx.position > ctx.previousPosition) {
    return {
      category: 'moved_down',
      line: pickFromFactories(LINES_MOVED_DOWN, ctx, recentLines),
    }
  }

  if (ctx.lastHit === true && ctx.rivalAhead && r < 0.55) {
    selections.push({
      category: 'hit_rival',
      line: pickFromFactories(LINES_HIT_RIVAL, ctx, recentLines),
    })
  }

  if (ctx.lastHit === true && r < 0.45) {
    selections.push({
      category: 'hit',
      line: pickFromLines(LINES_HIT, recentLines),
    })
  }

  if (ctx.lastHit === false && ctx.rivalBehind && r < 0.45) {
    selections.push({
      category: 'miss_rival',
      line: pickFromFactories(LINES_MISS_RIVAL, ctx, recentLines),
    })
  }

  if (ctx.lastHit === false && r < 0.35) {
    selections.push({
      category: 'miss',
      line: pickFromLines(LINES_MISS, recentLines),
    })
  }

  if (ctx.position && ctx.totalTeams) {
    if (ctx.position === 1 && r < 0.4) {
      selections.push({
        category: 'leading',
        line: pickFromLines(LINES_LEADING, recentLines),
      })
    }

    if (ctx.position === ctx.totalTeams && r < 0.3) {
      selections.push({
        category: 'last_place',
        line: pickFromLines(LINES_LAST_PLACE, recentLines),
      })
    }

    if (ctx.position === 2 && ctx.leaderTeam && r < 0.4) {
      selections.push({
        category: 'chasing_leader',
        line: pickFromFactories(LINES_CHASING_LEADER, ctx, recentLines),
      })
    }
  }

  if (ctx.rivalAhead && r < 0.28) {
    selections.push({
      category: 'rival_ahead',
      line: pickFromFactories(LINES_RIVAL_AHEAD, ctx, recentLines),
    })
  }

  if (ctx.rivalBehind && r < 0.24) {
    selections.push({
      category: 'rival_behind',
      line: pickFromFactories(LINES_RIVAL_BEHIND, ctx, recentLines),
    })
  }

  if (ctx.accuracy != null && ctx.accuracy >= 75 && r < 0.2) {
    selections.push({
      category: 'accuracy',
      line: pickFromFactories(LINES_ACCURACY, ctx, recentLines),
    })
  }

  if (ctx.level === 'max' && r < 0.5) {
    selections.push({
      category: 'max',
      line: pickFromLines(LINES_MAX, recentLines),
    })
  }

  if (ctx.level === 'hype' && r < 0.4) {
    selections.push({
      category: 'hype',
      line: pickFromLines(LINES_HYPE, recentLines),
    })
  }

  if (ctx.level === 'chill' && r < 0.3) {
    selections.push({
      category: 'chill',
      line: pickFromLines(LINES_CHILL, recentLines),
    })
  }

  selections.push({
    category: 'general',
    line: pickFromLines(LINES_GENERAL, recentLines),
  })

  return choosePreferredSelection(selections, recentCategories)
}

function minimumDisplayMs(line: string, baseIntervalMs: number): number {
  const words = line.trim().split(/\s+/).filter(Boolean).length
  const pauseMarkers = line.match(/[,:;.!?]/g)?.length ?? 0
  return Math.max(baseIntervalMs, 2200 + words * 420 + pauseMarkers * 140)
}

const RECENT_LINES_LIMIT = 5
const RECENT_CATEGORIES_LIMIT = 2

export function useCommentary(intervalMs = 5000): string {
  const hype = useHypeLevel()
  const { data: team } = useTeamRanking()
  const { data: ranking } = useRanking()
  const preds = normalizePredictions(team?.predictions)
  const last = preds[preds.length - 1]
  const lastTimestamp = last?.timestamp
  const lastCorrect = last?.correct
  const lastTsRef = useRef<string | undefined>(undefined)
  const previousPositionRef = useRef<number | undefined>(undefined)
  const previousHypeLevelRef = useRef<CommentContext['level'] | undefined>(undefined)
  const previousLeaderRef = useRef<string | undefined>(undefined)
  const previousRivalAheadRef = useRef<string | undefined>(undefined)
  const previousRivalBehindRef = useRef<string | undefined>(undefined)
  const nextLineAllowedAtRef = useRef<number>(0)
  const pendingSelectionRef = useRef<CommentSelection | undefined>(undefined)
  const lineHistoryRef = useRef<string[]>([])
  const categoryHistoryRef = useRef<LineCategory[]>([])
  const rankingEntries = withResolvedPositions(normalizeRanking(ranking))
  const { us, leader, rivalAhead, rivalBehind } = findTableContext(rankingEntries)

  const baseContext = useMemo<CommentContext>(() => ({
    level: hype.level,
    position: us?.resolvedPosition,
    totalTeams: rankingEntries.length,
    leaderTeam: leader?.team,
    rivalAhead: rivalAhead?.team,
    rivalBehind: rivalBehind?.team,
    accuracy: team?.accuracy,
  }), [hype.level, us?.resolvedPosition, rankingEntries.length, leader?.team, rivalAhead?.team, rivalBehind?.team, team?.accuracy])

  const [selection, setSelection] = useState<CommentSelection>(() => chooseLine(baseContext))
  const line = selection.line

  useEffect(() => {
    nextLineAllowedAtRef.current = Date.now() + minimumDisplayMs(line, intervalMs)

    lineHistoryRef.current = [
      line,
      ...lineHistoryRef.current.filter((previousLine) => previousLine !== line),
    ].slice(0, RECENT_LINES_LIMIT)

    categoryHistoryRef.current = [
      selection.category,
      ...categoryHistoryRef.current.filter((previousCategory) => previousCategory !== selection.category),
    ].slice(0, RECENT_CATEGORIES_LIMIT)
  }, [selection, line, intervalMs])

  useEffect(() => {
    const lastHit = lastTimestamp && lastTimestamp !== lastTsRef.current
      ? lastCorrect
      : undefined
    const previousPosition = previousPositionRef.current
    const positionChanged = previousPosition != null
      && baseContext.position != null
      && previousPosition !== baseContext.position
    const hypeChanged = previousHypeLevelRef.current != null
      && previousHypeLevelRef.current !== baseContext.level
    const leaderChanged = previousLeaderRef.current != null
      && previousLeaderRef.current !== baseContext.leaderTeam
    const rivalAheadChanged = previousRivalAheadRef.current != null
      && previousRivalAheadRef.current !== baseContext.rivalAhead
    const rivalBehindChanged = previousRivalBehindRef.current != null
      && previousRivalBehindRef.current !== baseContext.rivalBehind
    const shouldReplaceImmediately = lastHit !== undefined
      || positionChanged
      || hypeChanged
      || leaderChanged
      || rivalAheadChanged
      || rivalBehindChanged

    if (shouldReplaceImmediately) {
      const nextEventSelection = chooseLine({
        ...baseContext,
        lastHit,
        previousPosition,
      }, lineHistoryRef.current, categoryHistoryRef.current)

      if (Date.now() >= nextLineAllowedAtRef.current) {
        pendingSelectionRef.current = undefined
        setSelection(nextEventSelection)
      } else {
        pendingSelectionRef.current = nextEventSelection
      }
    }

    if (lastTimestamp) {
      lastTsRef.current = lastTimestamp
    }
    previousPositionRef.current = baseContext.position
    previousHypeLevelRef.current = baseContext.level
    previousLeaderRef.current = baseContext.leaderTeam
    previousRivalAheadRef.current = baseContext.rivalAhead
    previousRivalBehindRef.current = baseContext.rivalBehind
  }, [baseContext, lastCorrect, lastTimestamp])

  useEffect(() => {
    const delay = pendingSelectionRef.current
      ? Math.max(0, nextLineAllowedAtRef.current - Date.now())
      : Math.max(intervalMs, nextLineAllowedAtRef.current - Date.now())

    const id = window.setTimeout(() => {
      if (pendingSelectionRef.current) {
        const pendingSelection = pendingSelectionRef.current
        pendingSelectionRef.current = undefined
        setSelection(pendingSelection)
        return
      }

      setSelection(
        chooseLine({
          ...baseContext,
          previousPosition: previousPositionRef.current,
        }, lineHistoryRef.current, categoryHistoryRef.current),
      )
    }, delay)

    return () => window.clearTimeout(id)
  }, [selection, baseContext, intervalMs])

  return line
}