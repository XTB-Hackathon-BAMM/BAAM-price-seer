# Strategie predykcji — Forex (EUR/USD, GBP/JPY, USD/JPY)

## Charakterystyka klasy

- Handel 24/5 (zamknięty w weekendy)
- Trzy pary o bardzo różnych charakterystykach — traktuj je oddzielnie
- Na 1-min forex jest bardziej **mean-reverting** niż crypto — ruchy są krótsze, częściej oscyluje
- Remisy (`|close - open| < 0.0001`) zdarzają się częściej niż w crypto → silniejszy bias w stronę UP
- Zmienność zależy silnie od **sesji handlowej** — to kluczowa różnica vs crypto
- Dane dostępne: OHLC × ostatnie 20 świeczek 1-minutowych

---

## Charakterystyki par — różnice krytyczne

| Para | Charakter | Zmienność | Styl na 1-min |
|------|-----------|-----------|----------------|
| EUR/USD | Najbardziej płynna na świecie | Niska–średnia | Prawie losowy, dużo remisów → **UP bias** |
| GBP/JPY | "The Beast" | Wysoka | Silny momentum, trenduje jak crypto |
| USD/JPY | Płynny, "bezpieczna przystań" | Średnia | Śledzi sentyment ryzyka, korelacja z akcjami US |

---

## Sesje handlowe — kontekst dla wszystkich par

| Sesja | UTC | Charakterystyka |
|-------|-----|-----------------|
| Azjatycka | 00:00–07:00 | Niski wolumen, ciasne zakresy, dużo remisów → **UP** |
| London open | 07:00–09:00 | Wybuch zmienności, silne ruchy kierunkowe → momentum |
| Środek sesji europejskiej | 09:00–13:00 | Zmienna — może trendować lub oscylować |
| Overlap London–NY | 13:00–16:00 | Największa zmienność → momentum |
| Sesja NY | 16:00–20:00 | Umiarkowana zmienność |
| Dead zone | 20:00–00:00 | Brak wolumenu → **UP** |

---

## Krok 0 — Volatility Regime Detection (wspólny dla wszystkich par)

**Hipoteza:** Tak samo jak w crypto, forex przełącza się między reżimami — ciszą i aktywnością. Reżim determinuje całą dalszą strategię.

**Logika:**
```
std_returns = odchylenie standardowe zwrotów z ostatnich 5 świeczek
zwrot(t)    = (close(t) - open(t)) / open(t)

QUIET    (std < 0.00005): → UP (remisy, brak kierunku)
NORMAL   (std 0.00005–0.0002): → pełne drzewo per para
TRENDING (std > 0.0002): → momentum lub ATR filter
```

*Progi niższe niż dla crypto — forex jest mniej zmienny. Do kalibracji per para.*

**Uwaga:** Reżim QUIET pokrywa się z sesją azjatycką i dead zone — to naturalne. Jeśli std_returns jest niski w środku sesji londyńskiej, to też sygnał do UP (konsolidacja przed newsem).

---

## Strategie bazowe (używane w hierarchiach)

### Small Candle → UP

**Hipoteza:** Małe body = potencjalny remis = UP wygrywa. Najważniejszy filtr dla forex.

```
body  = |close - open|
ATR14 = Average True Range z 14 świeczek

body < ATR14 * 0.25 → UP
body >= ATR14 * 0.25 → przejdź dalej
```

---

### Mean Reversion (n=3)

**Hipoteza:** Po 3 świeczkach z rzędu w tym samym kierunku forex tenduje do odwrotu. n=2 jest zbyt agresywne — naprzemienne świeczki (UP/DOWN/UP) są normą i nie niosą informacji.

```
streak = liczba ostatnich świeczek z rzędu w tym samym kierunku

streak >= 3 i ostatnie były UP   → predykuj DOWN
streak >= 3 i ostatnie były DOWN → predykuj UP
streak < 3                       → przejdź dalej
```

---

### RSI Mean Reversion (uproszczony)

**Hipoteza:** Tylko skrajne ekstrema RSI są wiarygodnym sygnałem na 1-min. Strefy pośrednie generują zbyt wiele fałszywych sygnałów.

```
RSI(14) z closes

RSI < 25 → UP  (mocno oversold)
RSI > 75 → DOWN (mocno overbought)
25–75    → brak sygnału → przejdź dalej
```

---

### Counter-Trend po dużej świeczce

**Hipoteza:** Świeczka > 2× ATR to prawdopodobne przereagowanie — "rubber band" efekt. Korekta jest bardziej prawdopodobna niż kontynuacja.

```
body_last = |close - open| ostatniej świeczki
ATR14     = Average True Range z 14 świeczek

body_last > ATR14 * 2.0 → predykuj kierunek PRZECIWNY
body_last <= ATR14 * 2.0 → brak sygnału → przejdź dalej
```

*Nie stosować dla GBP/JPY — "The Beast" może kontynuować duży ruch przez kolejne minuty.*

---

### Bollinger Bands — Mean Reversion (wariant forex)

**Hipoteza:** Na forex 1-min breakouty z pasm Bollingera są najczęściej false breakoutami — cena wraca. Squeeze (wąskie pasmo) oznacza konsolidację → UP. Wyjście za pasmo → mean reversion (odwrotnie niż w crypto).

```
SMA20      = średnia z 20 closes
std20      = odchylenie standardowe z 20 closes
upper      = SMA20 + 2 * std20
lower      = SMA20 - 2 * std20
band_width = upper - lower
avg_width  = średnia band_width z 10 ostatnich świeczek

Squeeze (band_width < avg_width * 0.5):
    → UP (konsolidacja, remis prawdopodobny)

close > upper:
    → DOWN (false breakout — powrót do środka)

close < lower:
    → UP (false breakout — powrót do środka)

close w paśmie:
    → Mean Reversion (n=3) lub przejdź dalej
```

*Odwrotnie niż w crypto: forex na breakoucie WRACA, crypto KONTYNUUJE.*

---

### ATR-filtered Momentum (głównie GBP/JPY)

**Hipoteza:** GBP/JPY generuje duże świeczki podczas trendu — duże body = realny impuls, kontynuuj. Małe body = szum → UP.

```
body > ATR14 * 0.5 → Momentum (kierunek ostatniej świeczki)
body < ATR14 * 0.5 → UP
```

---

### USD/JPY Risk-Proxy

**Hipoteza:** USD/JPY rośnie przy risk-on (akcje US rosną), spada przy risk-off (inwestorzy uciekają do JPY). Korelacja silna podczas sesji NY, gdy oba rynki są aktywne jednocześnie.

```
Tylko podczas sesji NY (13:30–20:00 UTC):

Jeśli AAPL UP I MSFT UP (oba w tym samym kierunku):
    → predykuj USD/JPY UP (risk-on)

Jeśli AAPL DOWN I MSFT DOWN (oba w tym samym kierunku):
    → predykuj USD/JPY DOWN (risk-off)

Jeśli sprzeczne sygnały lub brak danych:
    → przejdź do Mean Reversion (n=3)
```

*Warunek AND (nie OR) — sygnał sprzeczny (AAPL UP, MSFT DOWN) oznacza brak konsensusu rynkowego.*

---

## Pełna hierarchia decyzji per para

### EUR/USD

```
START
  │
  ▼
[Krok 0] Volatility Regime
  │
  ├─ QUIET (std < 0.00005) ─────────────────────→ UP
  │
  └─ NORMAL / TRENDING
        │
        ▼
      [Krok 1] Small Candle filter
        │
        ├─ body < ATR * 0.25 ─────────────────→ UP
        │
        └─ body >= ATR * 0.25
              │
              ▼
            [Krok 2] RSI(14)
              │
              ├─ RSI < 25 ──────────────────→ UP
              ├─ RSI > 75 ──────────────────→ DOWN
              │
              └─ RSI 25–75
                    │
                    ▼
                  [Krok 3] Mean Reversion
                    │
                    ├─ streak >= 3 ────────→ kierunek przeciwny
                    │
                    └─ streak < 3
                          │
                          ▼
                        Fallback ──────────→ UP
```

*EUR/USD fallback to UP (nie Pure Momentum) — para jest na tyle losowa, że UP bias jest lepszym domyślnym niż śledzenie ostatniej świeczki.*

---

### GBP/JPY

```
START
  │
  ▼
[Krok 0] Sesja azjatycka? (UTC < 07:00)
  │
  ├─ TAK ───────────────────────────────────────→ UP
  │
  └─ NIE (sesja aktywna)
        │
        ▼
      [Krok 1] Volatility Regime
        │
        ├─ QUIET ─────────────────────────────→ UP
        │
        └─ NORMAL / TRENDING
              │
              ▼
            [Krok 2] ATR-filtered Momentum
              │
              ├─ body > ATR * 0.5 ──────────→ Momentum (kierunek ostatniej świeczki)
              │
              └─ body < ATR * 0.5
                    │
                    ▼
                  [Krok 3] Mean Reversion
                    │
                    ├─ streak >= 3 ────────→ kierunek przeciwny
                    │
                    └─ streak < 3
                          │
                          ▼
                        Fallback ──────────→ Pure Momentum
```

*GBP/JPY nie używa RSI ani BB — para trenduje zbyt silnie, żeby mean-reversion RSI była wiarygodna. Counter-Trend po dużej świeczce też wyłączony.*

---

### USD/JPY

```
START
  │
  ▼
[Krok 0] Sesja azjatycka? (UTC < 07:00)
  │
  ├─ TAK ───────────────────────────────────────→ UP
  │
  └─ NIE
        │
        ▼
      [Krok 1] Volatility Regime
        │
        ├─ QUIET ─────────────────────────────→ UP
        │
        └─ NORMAL / TRENDING
              │
              ▼
            [Krok 2] Counter-Trend po dużej świeczce
              │
              ├─ body > ATR * 2.0 ──────────→ kierunek PRZECIWNY
              │
              └─ body <= ATR * 2.0
                    │
                    ▼
                  [Krok 3] Risk-Proxy (tylko sesja NY 13:30–20:00 UTC)
                    │
                    ├─ AAPL I MSFT UP ────→ UP
                    ├─ AAPL I MSFT DOWN ──→ DOWN
                    │
                    └─ brak konsensusu
                          │
                          ▼
                        [Krok 4] RSI(14)
                          │
                          ├─ RSI < 25 ────→ UP
                          ├─ RSI > 75 ────→ DOWN
                          │
                          └─ RSI 25–75
                                │
                                ▼
                              [Krok 5] Mean Reversion
                                │
                                ├─ streak >= 3 → kierunek przeciwny
                                └─ streak < 3  → UP (fallback)
```

---

## Parametry do kalibracji

| Parametr | EUR/USD | GBP/JPY | USD/JPY | Wpływ |
|----------|---------|---------|---------|-------|
| std QUIET próg | 0.00005 | 0.00005 | 0.00005 | wyższy → więcej UP w ciszy |
| std TRENDING próg | 0.0002 | 0.0003 | 0.0002 | wyższy → rzadziej TRENDING |
| Small candle (ATR ×) | 0.25 | — | 0.25 | wyższy → więcej UP |
| Mean Reversion streak | 3 | 3 | 3 | niższy → częstszy reversal |
| Counter-Trend próg (ATR ×) | — | — | 2.0 | wyższy → rzadszy counter-trend |
| ATR Momentum próg (ATR ×) | — | 0.5 | — | wyższy → ostrożniejszy momentum |
| RSI oversold | 25 | — | 25 | niższy → rzadszy, pewniejszy |
| RSI overbought | 75 | — | 75 | wyższy → rzadszy, pewniejszy |
