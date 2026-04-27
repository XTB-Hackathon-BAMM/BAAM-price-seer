# Strategie predykcji — Crypto (BTC/USD, ETH/USD)

## Charakterystyka klasy

- Handel 24/7 — brak sesji, brak "godzin poza rynkiem"
- Wysoka zmienność — ruchy rzędu 0.1–0.5% na minutę są normą
- Silny momentum — trendy na 1-min trwają dłużej niż na forex
- BTC i ETH są wysoce skorelowane (~0.85–0.95); BTC często prowadzi, ETH podąża z lekkim opóźnieniem
- Dane dostępne: OHLC × ostatnie 20 świeczek 1-minutowych

---

## Krok 1 — Volatility Regime Detection (wejście do drzewa decyzji)

**Hipoteza:** Charakter rynku crypto zmienia się między fazami — trendem i konsolidacją. Każda faza wymaga innej strategii. Zanim cokolwiek predykujesz, określ reżim.

**Logika:**
```
std_returns = odchylenie standardowe zwrotów z ostatnich 5 świeczek
zwrot(t)    = (close(t) - open(t)) / open(t)

Jeśli std_returns > próg_wysoki (np. 0.002 = 0.2%):
    → TRENDING — rynek w ruchu kierunkowym → zastosuj strategie momentum

Jeśli std_returns < próg_niski (np. 0.0005 = 0.05%):
    → QUIET — konsolidacja, brak kierunku → predykuj UP (tie/szum bias)

Pomiędzy progami:
    → NORMAL — zastosuj pełne drzewo decyzji
```

**Dlaczego 5 świeczek:** Wystarczy do wychwycenia bieżącego reżimu bez nadmiernego opóźnienia. Więcej świeczek = wolniejsza reakcja na zmianę reżimu.

**Parametry:** próg_wysoki (0.002), próg_niski (0.0005) — do kalibracji per instrument.

---

## Krok 2 — Sygnały per reżim

### Reżim QUIET → zawsze UP

Małe świeczki = potencjalne remisy = UP wygrywa. Bez dalszej analizy.

---

### Reżim TRENDING → 3-krokowe drzewo

#### Krok 2a — ATR-filtered Momentum

**Hipoteza:** Duże świeczki względem ATR sygnalizują realny impuls kierunkowy — kontynuuj. Małe świeczki to szum mimo trendu — fallback do UP.

```
ATR(14) = średnia True Range z 14 świeczek
True Range(t) = max(high - low, |high - close_prev|, |low - close_prev|)
body = |close - open| ostatniej świeczki

body > ATR * 0.7  → silny impuls → Momentum (kierunek ostatniej świeczki)
body < ATR * 0.3  → szum       → UP
body pomiędzy     → przejdź do Kroku 2b
```

#### Krok 2b — RSI z ostrymi progami

**Hipoteza:** RSI w skrajnym ekstremum (nie standardowe 30/70 — zbyt szerokie na 1-min) sygnalizuje wyczerpanie impulsu i odwrócenie.

```
RSI(14) z closes

RSI < 20  → mocno oversold → UP  (silne odreagowanie)
RSI > 80  → mocno overbought → DOWN (silna korekta)
20–80     → brak sygnału RSI → przejdź do Kroku 2c
```

**Uwaga:** Progi 20/80 zamiast klasycznych 30/70 — na 1-min crypto RSI zbyt często trafia w 30/70 podczas normalnych ruchów, generując fałszywe sygnały. Ekstrema 20/80 są rzadsze i bardziej wiarygodne.

#### Krok 2c — Pure Momentum (fallback)

```
close >= open ostatniej świeczki → UP
close < open                     → DOWN
Brak historii                    → UP
```

---

### Reżim NORMAL → pełne drzewo (2a → 2b → 2c)

---

## Krok 3 — BTC jako leading indicator dla ETH (tylko ETH/USD)

**Hipoteza:** BTC prowadzi price discovery w kryptowalutach — ETH podąża z opóźnieniem 1–2 minut. Ruch BTC jest silniejszym sygnałem dla ETH niż własna historia ETH.

**Zastosowanie:** Uruchom PRZED krokami 2a–2c, jako nadrzędny sygnał.

```
Tylko dla ETH/USD:

body_BTC = |close - open| ostatniej świeczki BTC/USD
ATR_BTC  = ATR(14) dla BTC/USD

Jeśli body_BTC > ATR_BTC * 0.5  (silny ruch BTC):
    BTC close > BTC open → predykuj ETH UP
    BTC close < BTC open → predykuj ETH DOWN
    (pomiń kroki 2a–2c)

Jeśli body_BTC <= ATR_BTC * 0.5 (słaby/brak sygnału BTC):
    → zastosuj kroki 2a–2c na danych ETH
```

**Uwaga implementacyjna:** Wymaga dostępu do historii BTC podczas predykcji ETH — obydwa instrumenty muszą być w tym samym repozytorium cen.

---

## Strategia pomocnicza — Doji → UP

**Hipoteza:** Świeczka doji (body minimalne względem całego zakresu) = niezdecydowanie rynku. Remis wygrywa UP — to bezpośrednie wykorzystanie reguły scoringu.

```
body   = |close - open|
shadow = high - low

Jeśli shadow > 0:
    body / shadow < 0.15 → doji → UP (nadpisz wynik kroku 2)

Jeśli shadow == 0:
    body == 0 → UP (idealna świeczka flat)
    body > 0  → Pure Momentum (bez dzielenia)
```

**Zastosowanie:** Filtr nakładany PO krokach 2a–2c — jeśli świeczka jest doji, nadpisz wynik na UP niezależnie od wskaźników.

---

## Pełna hierarchia decyzji

### BTC/USD

```
START
  │
  ▼
[Krok 1] Oblicz std_returns(5)
  │
  ├─ QUIET (std < 0.0005) ──────────────────────────→ UP
  │
  ├─ TRENDING lub NORMAL
  │     │
  │     ▼
  │   [Krok 2a] ATR filter
  │     │
  │     ├─ body > ATR * 0.7 ──────────────────────→ Momentum
  │     │
  │     ├─ body < ATR * 0.3 ──────────────────────→ UP
  │     │
  │     └─ body pomiędzy
  │           │
  │           ▼
  │         [Krok 2b] RSI(14)
  │           │
  │           ├─ RSI < 20 ───────────────────────→ UP
  │           ├─ RSI > 80 ───────────────────────→ DOWN
  │           └─ RSI 20–80
  │                 │
  │                 ▼
  │               [Krok 2c] Pure Momentum ────────→ kierunek ostatniej świeczki
  │
  ▼
[Filtr Doji] body/shadow < 0.15? → nadpisz na UP
  │
  ▼
WYNIK
```

---

### ETH/USD

```
START
  │
  ▼
[Krok 3] BTC signal
  │
  ├─ body_BTC > ATR_BTC * 0.5 ──────────────────→ kierunek BTC (pomiń resztę)
  │
  └─ body_BTC <= ATR_BTC * 0.5
        │
        ▼
      [identyczne drzewo jak BTC/USD — kroki 1, 2a, 2b, 2c]
        │
        ▼
      [Filtr Doji]
        │
        ▼
      WYNIK
```

---

## Parametry do kalibracji

| Parametr | Domyślna wartość | Wpływ |
|----------|-----------------|-------|
| std_returns próg_wysoki | 0.002 | zbyt wysoki → za mało TRENDING, za dużo NORMAL |
| std_returns próg_niski | 0.0005 | zbyt niski → za dużo QUIET → za dużo UP |
| ATR body próg duży | 0.7 | wyższy → rzadziej momentum, ostrożniej |
| ATR body próg mały | 0.3 | niższy → więcej UP (szum) |
| RSI oversold | 20 | niższy = rzadszy, pewniejszy sygnał |
| RSI overbought | 80 | wyższy = rzadszy, pewniejszy sygnał |
| BTC lead threshold | 0.5 × ATR_BTC | wyższy → rzadziej używa BTC jako sygnału |
| Doji body/shadow | 0.15 | wyższy → więcej świeczek klasyfikowane jako doji |
