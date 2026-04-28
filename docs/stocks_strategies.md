# Strategie predykcji — Akcje US (AAPL, MSFT)

## Charakterystyka klasy

- Handel tylko podczas sesji NYSE/NASDAQ: **13:30–20:00 UTC** (9:30–16:00 ET)
- Poza sesją: ceny prawie nie ruszają → dominują remisy → **UP wygrywają**
- AAPL i MSFT są silnie skorelowane (~0.80–0.90) — obydwie to big tech, reagują na te same makro
- Zmienność jest nierówna w ciągu sesji — intraday ma wyraźną strukturę fazową
- Dane dostępne: OHLC × ostatnie 20 świeczek 1-minutowych

---

## Krytyczny kontekst: godziny hackathonu vs. sesja NYSE

Hackathon odbywa się w Polsce (CEST = UTC+2). Sesja NYSE otwiera się o **15:30 CEST**.

| Czas CEST | UTC | Faza |
|-----------|-----|------|
| do 15:30 | do 13:30 | Poza sesją → **zawsze UP** |
| 15:30–16:00 | 13:30–14:00 | Opening — gap momentum |
| 16:00–19:00 | 14:00–17:00 | Sesja poranna — momentum |
| 19:00–20:30 | 17:00–18:30 | Lunch — mean reversion |
| 20:30–22:00 | 18:30–20:00 | Closing — silny momentum MOC |

**Jeśli hackathon startuje przed 15:30 CEST** — AAPL i MSFT są poza sesją przez większość czasu → Always UP dominuje bez żadnej logiki technicznej.

---

## Krok 0 — Volatility Regime Detection

**Hipoteza:** Dla akcji reżim jest prostszy niż dla crypto/forex — poza sesją ATR jest bliski zera, co automatycznie wykrywa "flat market" bez patrzenia na zegarek.

**Logika:**
```
ATR14 = Average True Range z 14 świeczek
body  = |close - open| ostatniej świeczki

FLAT   (ATR14 < 0.05 USD lub body < ATR14 * 0.15): → UP
ACTIVE (ATR14 >= 0.05 USD): → pełne drzewo per faza
```

*Próg 0.05 USD jest orientacyjny — w sesji AAPL porusza się 0.10–0.50 USD/min, poza sesją < 0.02 USD/min. Do kalibracji per instrument.*

**Uwaga:** Volatility Regime wykrywa poza-sesyjną ciszę niezależnie od godziny — działa jako backup dla time gate'u, a nie jego zamiennik.

---

## Strategie bazowe

### Always UP poza sesją (Time Gate)

**Hipoteza:** Poza NYSE wolumen jest minimalny, spread szeroki, ceny stoją. Remisy dominują → UP wygrywa.

```
UTC < 13:30 lub UTC >= 20:00 → UP (zawsze, bez wyjątku)
```

*Pierwsza i bezwzględna reguła — żaden wskaźnik techniczny nie ma sensu przy płaskich cenach.*

---

### Opening Gap Momentum (13:30–14:00 UTC)

**Hipoteza:** Pierwsza świeczka po otwarciu zawiera akumulację zleceń premarket i nocnych. Kierunek dużego otwarcia trwa przez pierwsze kilka minut — "opening drive".

```
Tylko UTC 13:30–14:00:

body_open = |close - open| pierwszej świeczki (13:30)
ATR14     = ATR z ostatnich 14 świeczek (w tym poza-sesyjnych)

body_open > ATR14 * 1.5:
    → duże otwarcie → Pure Momentum (kontynuuj kierunek przez całą fazę Opening)

body_open <= ATR14 * 1.5:
    → spokojne otwarcie → UP (brak wyraźnego impulsu)
```

*"Gap and reverse" istnieje ale jest mniej powszechny niż kontynuacja — momentum jest statystycznie lepszym domyślnym przy dużym otwarciu.*

---

### ATR-Filtered Momentum (sesja poranna 14:00–17:00 UTC)

**Hipoteza:** W środku sesji duże świeczki = realny impuls → kontynuuj. Małe świeczki = konsolidacja lub brak kierunku → UP.

```
body > ATR14 * 0.6 → Momentum (kierunek ostatniej świeczki)
body < ATR14 * 0.25 → UP
0.25–0.6            → SMA Trend Filter
```

---

### SMA Trend Filter

**Hipoteza:** Kierunek krótkoterminowej średniej względem długoterminowej wychwytuje kilkuminutowy trend akcji podczas aktywnej sesji.

```
SMA_short = SMA(3)  z closes
SMA_long  = SMA(10) z closes

SMA_short > SMA_long → UP
SMA_short < SMA_long → DOWN
SMA_short ≈ SMA_long → Pure Momentum
```

---

### Lunch Mean Reversion (17:00–18:30 UTC)

**Hipoteza:** Wolumen spada w lunch time US, market makerzy wycofują się, ceny oscylują bez kierunku. N=3 (nie 2) — n=2 triggeruje przy każdej naprzemiennej parze, co jest normą i nie niesie informacji.

```
Tylko UTC 17:00–18:30:

streak >= 3 i UP   → DOWN
streak >= 3 i DOWN → UP
streak < 3         → UP (fallback — brak kierunku)
```

---

### Closing Momentum (18:30–20:00 UTC)

**Hipoteza:** Market-On-Close (MOC) zlecenia funduszy tworzają wyraźny trend w ostatniej godzinie sesji. Ostatnie 30 minut (19:30–20:00) jest szczególnie silne.

```
Tylko UTC 18:30–20:00:

SMA_short = SMA(3)  z closes
SMA_long  = SMA(10) z closes

SMA_short > SMA_long → UP
SMA_short < SMA_long → DOWN
SMA_short ≈ SMA_long → Pure Momentum
```

---

### RSI (uproszczony, spójny z pozostałymi plikami)

**Hipoteza:** Skrajne ekstrema RSI na 1-min akcji sygnalizują krótkoterminowe przegrzanie lub wyprzedanie. Progi 25/75 (nie 30/70) — ostrzejsze, rzadsze, pewniejsze.

```
Tylko podczas sesji (13:30–20:00 UTC):

RSI(14) z closes

RSI < 25 → UP   (oversold)
RSI > 75 → DOWN (overbought)
25–75    → brak sygnału → przejdź dalej
```

---

### AAPL–MSFT Cross-Signal

**Hipoteza:** AAPL i MSFT są silnie skorelowane. Ruch jednej spółki jest sygnałem wyprzedzającym dla drugiej. AAPL → MSFT jest silniejszym sygnałem niż MSFT → AAPL (AAPL jest większy, bardziej płynny, częściej prowadzi price discovery).

```
Tylko podczas sesji (13:30–20:00 UTC):

Dla predykcji MSFT (silny sygnał):
    AAPL body > ATR(AAPL) * 0.4 I AAPL close > open → MSFT UP
    AAPL body > ATR(AAPL) * 0.4 I AAPL close < open → MSFT DOWN
    Słaby sygnał AAPL → Pure Momentum MSFT

Dla predykcji AAPL (słabszy sygnał — MSFT rzadziej prowadzi):
    MSFT body > ATR(MSFT) * 0.4 I MSFT close > open → AAPL UP (umiarkowany)
    MSFT body > ATR(MSFT) * 0.4 I MSFT close < open → AAPL DOWN (umiarkowany)
    Słaby sygnał MSFT → ATR-Filtered Momentum AAPL
```

---

## Pełna hierarchia decyzji

### AAPL i MSFT (wspólna struktura, różna siła cross-signalu)

```
START
  │
  ▼
[Krok 0] Volatility Regime
  │
  ├─ FLAT (ATR < 0.05 lub body < ATR * 0.15) ────────────→ UP
  │
  └─ ACTIVE
        │
        ▼
      [Krok 1] Time Gate
        │
        ├─ UTC < 13:30 lub UTC >= 20:00 ────────────────→ UP
        │
        └─ sesja aktywna (13:30–20:00)
              │
              ├─ UTC 13:30–14:00 (Opening)
              │     │
              │     ├─ body_open > ATR * 1.5 ──────────→ Pure Momentum
              │     └─ body_open <= ATR * 1.5 ─────────→ UP
              │
              ├─ UTC 17:00–18:30 (Lunch)
              │     │
              │     ├─ streak >= 3 ───────────────────→ Mean Reversion
              │     └─ streak < 3  ───────────────────→ UP
              │
              ├─ UTC 18:30–20:00 (Closing)
              │     │
              │     └─ SMA(3) vs SMA(10) ────────────→ kierunek SMA
              │
              └─ UTC 14:00–17:00 (Sesja poranna)
                    │
                    ▼
                  [Krok 2] Cross-Signal
                    │
                    ├─ silny sygnał z drugiej spółki ──→ kierunek cross
                    │
                    └─ brak sygnału
                          │
                          ▼
                        [Krok 3] RSI(14)
                          │
                          ├─ RSI < 25 ──────────────→ UP
                          ├─ RSI > 75 ──────────────→ DOWN
                          │
                          └─ RSI 25–75
                                │
                                ▼
                              [Krok 4] ATR-Filtered Momentum
                                │
                                ├─ body > ATR * 0.6 ─→ Momentum
                                ├─ body < ATR * 0.25 → UP
                                │
                                └─ body pomiędzy
                                      │
                                      ▼
                                    SMA Trend Filter ─→ kierunek SMA
```

---

## Parametry do kalibracji

| Parametr | AAPL | MSFT | Wpływ |
|----------|------|------|-------|
| FLAT ATR próg (USD) | 0.05 | 0.08 | wyższy → więcej UP (szerszy "flat") |
| body/ATR FLAT próg | 0.15 | 0.15 | wyższy → więcej UP |
| Opening body próg (ATR ×) | 1.5 | 1.5 | wyższy → rzadziej Opening Momentum |
| Lunch streak n | 3 | 3 | niższy → agresywniejszy reversal |
| RSI oversold | 25 | 25 | niższy → rzadszy, pewniejszy |
| RSI overbought | 75 | 75 | wyższy → rzadszy, pewniejszy |
| Cross-Signal próg (ATR ×) | 0.4 | 0.4 | wyższy → rzadszy, silniejszy sygnał |
| ATR Momentum duży (ATR ×) | 0.6 | 0.6 | wyższy → ostrożniejszy momentum |
| ATR Momentum mały (ATR ×) | 0.25 | 0.25 | niższy → więcej UP (szum) |
