# Poprawka CryptoStrategy — RSI nie powinno działać w reżimie TRENDING

## Problem

Backtest ujawnił katastrofalną skuteczność w silnym downtrend: **29/100 (29%)**.
Dla porównania, AlwaysUP w tym samym scenariuszu dałoby ~19% — strategia jest więc
trochę lepsza niż nic, ale wyraźnie gorsza niż pure momentum.

```
BTC/USD — uptrend   →  61%   ✅
BTC/USD — downtrend →  29%   ❌  (AlwaysUP: ~19%, PureMomentum: ~81%)
BTC/USD — sideways  →  46%   ⚠️  (poniżej baseline 50%)
```

## Przyczyna

W silnym downtrend RSI spada poniżej 20 i **zostaje tam przez wiele świeczek z rzędu**.
Strategia interpretuje to jako oversold → zwraca UP. Rynek spada dalej.

Aktualna hierarchia dla TRENDING i NORMAL jest identyczna:

```
ATR filter → RSI (20/80) → Pure Momentum
```

RSI jest stosowane w obu reżimach. W reżimie NORMAL (umiarkowana zmienność) RSI < 20
faktycznie sygnalizuje krótkoterminowe przegrzanie — bounce jest prawdopodobny.
W reżimie TRENDING RSI < 20 oznacza **trend RSI** — rynek jest w silnym ruchu
i bounce nie nadchodzi. To klasyczny błąd: RSI mean-reversion w trendującym rynku.

## Poprawka

Usuń RSI z reżimu TRENDING. W silnym trendzie pozwól Pure Momentum zdecydować.

### Zmiana w `CryptoStrategy.decisionTree()`

```kotlin
// PRZED (identyczna logika dla obu aktywnych reżimów):
Regime.TRENDING, Regime.NORMAL -> {
    atrFilteredMomentum(history)
        ?: rsiSignal(history)
        ?: pureMomentum(history.last())
}

// PO (RSI tylko w NORMAL):
Regime.TRENDING -> {
    atrFilteredMomentum(history)
        ?: pureMomentum(history.last())
}
Regime.NORMAL -> {
    atrFilteredMomentum(history)
        ?: rsiSignal(history)
        ?: pureMomentum(history.last())
}
```

Zmiana dotyczy tylko metody `decisionTree` — jedna linijka rozdzielenia `when` branch.

## Oczekiwany efekt

| Scenariusz | Przed | Po (szacunek) |
|-----------|-------|---------------|
| Uptrend | 61% | ~65% (RSI overbought nie hamuje momentum) |
| Downtrend | 29% | ~70%+ (Pure Momentum podąża za trendem) |
| Sideways | 46% | ~46% (bez zmian — NORMAL reżim, RSI nadal działa) |
| ETH + BTC lead | 76% | ~76% (bez zmian) |

Downtrend to największy zysk — zamiast sygnalizować UP przez cały czas,
strategia zaczyna podążać za kierunkiem świeczki.

## Uzasadnienie

Zasada znana z analizy technicznej: RSI jako sygnał mean-reversion działa
w rynku bocznym (NORMAL), ale zawodzi w trendzie (TRENDING), gdzie może
pozostawać w strefie ekstremalnej przez dziesiątki świeczek.

ATR filter w TRENDING reżimie już odfiltrowuje silne impulsy. Gdy impuls
nie jest wystarczająco silny dla ATR, lepszym domyślnym jest kierunek
ostatniej świeczki (Pure Momentum) niż próba "złapania dołka" przez RSI.
