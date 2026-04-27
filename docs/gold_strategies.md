# Strategie predykcji — Złoto (XAU/USD)

## Charakterystyka klasy

- Handel prawie 24/5 (jak forex), ale aktywność silnie zależy od sesji
- **Safe haven** — złoto rośnie przy panice, spada przy "risk-on" (gdy inwestorzy kupują akcje)
- Cena wyrażona w USD → silna odwrotna korelacja z mocą dolara
- Trenduje wyraźniej niż EUR/USD, ale spokojniej niż crypto
- Wrażliwe na: dane makro US (NFP, CPI, Fed), geopolitykę, ruchy USD
- Na 1-min: w aktywnych sesjach momentum działa dobrze; w ciszy — UP
- Dane dostępne: OHLC × ostatnie 20 świeczek 1-minutowych
- Typowe wartości: ~2000–3000 USD/oz; ruch 1–5 USD/min to norma

---

## Kluczowe momenty dnia

| Czas UTC | Wydarzenie | Efekt na złoto |
|----------|------------|----------------|
| 07:00–09:00 | London open | Wzrost wolumenu, często breakout |
| 09:30 UTC | London AM Gold Fix | Możliwy krótki spike |
| 13:00–13:30 | Przed otwarciem NY | Rośnie zmienność |
| 13:30 UTC | Otwarcie NYSE + dane US (często) | Najsilniejszy ruch dnia |
| **14:00 UTC** | **London PM Gold Fix** | **Najważniejszy — instytucjonalne zlecenia, często przereagowanie** |
| 15:00 UTC | Dane makro US (ISM, itp.) | Gwałtowne ruchy |
| 20:00 UTC | Zamknięcie NYSE | Wolumen spada |

---

## Krok 0 — Volatility Regime Detection

**Hipoteza:** Złoto, podobnie jak forex, przełącza się między ciszą a aktywnością. Reżim determinuje całą dalszą strategię i eliminuje potrzebę twardych bram czasowych.

**Logika:**
```
std_returns = odchylenie standardowe zwrotów z ostatnich 5 świeczek
zwrot(t)    = (close(t) - open(t)) / open(t)

QUIET    (std < 0.0001):  → UP (brak kierunku, remisy)
NORMAL   (std 0.0001–0.0003): → pełne drzewo decyzji
TRENDING (std > 0.0003):  → ATR filter + momentum
```

*Progi wyższe niż forex, niższe niż crypto — złoto jest pośredniej zmienności.*

**Uwaga:** Reżim QUIET naturalnie pokrywa sesję azjatycką i czas po zamknięciu NYSE — nie trzeba osobnych bram czasowych. PM Fix (14:00 UTC) zawsze działa niezależnie od reżimu.

---

## Strategie bazowe

### ATR-Filtered Momentum

**Hipoteza:** Duże świeczki względem ATR = realny impuls → kontynuuj. Małe świeczki = szum → UP.

```
ATR14 = Average True Range z 14 świeczek
body  = |close - open| ostatniej świeczki

body > ATR14 * 0.8  → Momentum (kierunek ostatniej świeczki)
body < ATR14 * 0.3  → UP (szum/konsolidacja)
0.3–0.8             → SMA Trend Filter
```

---

### SMA Trend Filter (wzmocniony)

**Hipoteza:** Złoto trenduje wyraźniej niż forex — SMA crossover wychwytuje kierunek kilkuminutowego trendu. Wariant wzmocniony potwierdza trend przez sprawdzenie kierunku samej SMA_long.

```
SMA_short = SMA(3)  z closes
SMA_long  = SMA(10) z closes

SMA_short > SMA_long I SMA_long rośnie → silny uptrend   → UP
SMA_short < SMA_long I SMA_long spada  → silny downtrend → DOWN
Pozostałe przypadki (boczny, crossover) → Pure Momentum
```

*SMA_long "rośnie" = SMA10_now > SMA10 sprzed 3 świeczek.*

---

### RSI (uproszczony)

**Hipoteza:** Tylko skrajne ekstrema RSI są wiarygodnym sygnałem na 1-min. Strefy pośrednie generują fałszywe sygnały w systemie binarnych predykcji.

```
RSI(14) z closes

RSI < 25 → UP   (mocno oversold)
RSI > 75 → DOWN (mocno overbought)
25–75    → brak sygnału → przejdź dalej
```

---

### PM Fix Counter-Trend (14:00 UTC)

**Hipoteza:** O 14:00 UTC banki centralne i fundusze realizują duże zlecenia (London PM Gold Fix). Powoduje gwałtowny ruch, po którym następuje korekta w kolejnej minucie.

```
Tylko jeśli UTC w przedziale 14:00–14:05:

body_fix = |close - open| świeczki z minuty 14:00
ATR14    = Average True Range z 14 świeczek

body_fix > ATR14 * 2.0:
    close > open → predykuj DOWN (korekta po dużym UP)
    close < open → predykuj UP   (korekta po dużym DOWN)

body_fix <= ATR14 * 2.0:
    → brak silnego Fixu → kontynuuj drzewo decyzji
```

*Predykcja dotyczy NASTĘPNEJ minuty po Fixie — nie minuty Fixu. To standardowy tryb: widzisz świeczkę 14:00, wysyłasz predykcję dla 14:01.*

---

### Bollinger Bands — wariant adaptacyjny

**Hipoteza:** Złoto trenduje i może mieć realne breakouty (inaczej niż forex), ale w konsolidacji wraca do środka. Szerokość pasma decyduje, który wariant zastosować.

```
SMA20      = średnia z 20 closes
std20      = odchylenie standardowe z 20 closes
upper      = SMA20 + 2 * std20
lower      = SMA20 - 2 * std20
band_width = upper - lower
avg_width  = średnia band_width z 10 ostatnich świeczek

Squeeze (band_width < avg_width * 0.7):
    → UP (konsolidacja, remis prawdopodobny)

Trend (band_width > avg_width * 1.3):
    close > upper → UP   (breakout — kontynuuj)
    close < lower → DOWN (breakdown — kontynuuj)
    close w paśmie → SMA Trend Filter

Normalny (pomiędzy):
    close > upper → DOWN (powrót do środka)
    close < lower → UP   (powrót do środka)
    close w paśmie → SMA Trend Filter
```

---

### Risk Sentiment Proxy

**Hipoteza:** Złoto jest safe haven — odwrotna korelacja z risk-on. AAPL i MSFT jako proxy sentymentu rynku podczas sesji NY.

```
Tylko podczas sesji NY (13:30–20:00 UTC):

AAPL UP I MSFT UP   → XAU/USD DOWN (risk-on → złoto spada)
AAPL DOWN I MSFT DOWN → XAU/USD UP (risk-off → złoto rośnie)
Sprzeczne lub brak danych → przejdź dalej
```

*Warunek AND — sygnał sprzeczny (AAPL UP, MSFT DOWN) oznacza brak konsensusu.*

---

### Regression to Mean

**Hipoteza:** Jeśli cena odchyliła się znacznie od SMA(10), istnieje tendencja do powrotu. Uzupełnia RSI — RSI mierzy prędkość zmian, Regression mierzy odległość od średniej.

```
SMA10     = SMA(10) z closes
deviation = (close_last - SMA10) / SMA10 * 100  [w %]

deviation > +0.15%  → DOWN (powyżej średniej → powrót)
deviation < -0.15%  → UP   (poniżej średniej → powrót)
-0.15% do +0.15%    → brak sygnału → przejdź dalej
```

*Próg 0.15% ≈ 3–4 USD przy cenie złota ~2500 USD.*

---

## Pełna hierarchia decyzji

```
START
  │
  ▼
[Krok 0] Volatility Regime
  │
  ├─ QUIET (std < 0.0001) ──────────────────────────────────→ UP
  │
  └─ NORMAL / TRENDING
        │
        ▼
      [Krok 1] PM Fix? (UTC 14:00–14:05 I body > ATR * 2.0)
        │
        ├─ TAK ───────────────────────────────────────────→ Counter-Trend
        │
        └─ NIE
              │
              ▼
            [Krok 2] ATR filter
              │
              ├─ body > ATR * 0.8 ──────────────────────→ Momentum
              │
              ├─ body < ATR * 0.3 ──────────────────────→ UP
              │
              └─ body 0.3–0.8
                    │
                    ▼
                  [Krok 3] RSI(14)
                    │
                    ├─ RSI < 25 ──────────────────────→ UP
                    ├─ RSI > 75 ──────────────────────→ DOWN
                    │
                    └─ RSI 25–75
                          │
                          ▼
                        [Krok 4] Regression to Mean
                          │
                          ├─ deviation > +0.15% ──────→ DOWN
                          ├─ deviation < -0.15% ──────→ UP
                          │
                          └─ brak sygnału
                                │
                                ▼
                              [Krok 5] Risk Proxy
                              (tylko sesja NY 13:30–20:00)
                                │
                                ├─ AAPL I MSFT UP ──→ DOWN
                                ├─ AAPL I MSFT DOWN → UP
                                │
                                └─ brak konsensusu
                                      │
                                      ▼
                                    [Krok 6] Bollinger Bands
                                      │
                                      ├─ squeeze ─────→ UP
                                      ├─ breakout ────→ Momentum
                                      ├─ normalny ────→ Mean Reversion
                                      │
                                      └─ w paśmie
                                            │
                                            ▼
                                          Fallback ──→ SMA Trend Filter
```

---

## Parametry do kalibracji

| Parametr | Wartość domyślna | Wpływ |
|----------|-----------------|-------|
| std QUIET próg | 0.0001 | wyższy → więcej UP w ciszy |
| std TRENDING próg | 0.0003 | wyższy → rzadziej TRENDING |
| ATR body próg duży | 0.8 | wyższy → ostrożniejszy momentum |
| ATR body próg mały | 0.3 | niższy → więcej UP (szum) |
| PM Fix próg (ATR ×) | 2.0 | wyższy → rzadszy counter-trend |
| RSI oversold | 25 | niższy → rzadszy, pewniejszy |
| RSI overbought | 75 | wyższy → rzadszy, pewniejszy |
| Regression próg | 0.15% | wyższy → rzadszy, pewniejszy |
| BB squeeze próg | 0.7 × avg | niższy → rzadziej squeeze |
| BB trend próg | 1.3 × avg | wyższy → rzadziej Breakout wariant |
