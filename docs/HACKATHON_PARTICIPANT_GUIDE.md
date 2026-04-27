# Oracle Hackathon — Przewodnik Uczestnika

## Czym jest Oracle?

Oracle to serwis, który co minutę pobiera ceny 8 instrumentów finansowych i ocenia predykcje drużyn. Zadanie drużyny to **przewidzieć kierunek ruchu ceny** (UP lub DOWN) dla wybranego instrumentu w danej minucie. Im więcej trafień w ostatnich 100 minutach — tym wyższe miejsce w rankingu.

---

## Nagrody

### 🥇 Nagroda za najlepszą predykcję
Zespół z **najwyższą trafnością** w ostatnich 100 predykcjach dla 8 instrumentów. Tu się nie dyskutuje — liczby mówią same za siebie.

### 🥈 Nagroda jury - Złoty Miód 
Jury ocenia:
- **Jakość kodu i architektury** — czy jest czytelny, przemyślany, skalowalny?
- **Pomysł na predykcję** — co sprawia, że Wasza strategia jest wyjątkowa?
- **Wykorzystanie technologii** — jak użyliście wymaganych narzędzi i AI?
- **Frontend** — może warto dobierać muzykę w zależności od tego, czy pewniaczki wchodzą? 🎵

### Nagroda publiczności
Za **prezentację**. Głosuje publiczność po demo. Pokażcie co zrobiliście i przekonajcie resztę, że warto było nie spać.


## Infrastruktura — adres serwera

Wszystkie serwisy działają na maszynie pod adresem **`192.168.8.244`**:

| Serwis | Adres |
|--------|-------|
| Oracle API | `http://192.168.8.244:8080` |
| Dashboard (ranking live) | `http://192.168.8.244:8080/dashboard.html` |
| Swagger UI | `http://192.168.8.244:8080/swagger.html` |
| Kafka broker | `192.168.8.244:9092` |
| Kafka UI (przeglądarka topików) | `http://192.168.8.244:8081` |

> Upewnij się, że Twoja maszyna ma dostęp sieciowy do `192.168.8.244`.

---

## Instrumenty

Oracle obsługuje dokładnie **8 instrumentów**. Używaj tych symboli dosłownie:

| Symbol | Opis |
|--------|------|
| `AAPL` | Akcje Apple |
| `MSFT` | Akcje Microsoft |
| `EUR/USD` | Euro / Dolar |
| `GBP/JPY` | Funt / Jen |
| `BTC/USD` | Bitcoin / Dolar |
| `ETH/USD` | Ethereum / Dolar |
| `XAU/USD` | Złoto / Dolar |
| `USD/JPY` | Dolar / Jen |

Aktualną listę możesz pobrać z API:
```bash
curl http://192.168.8.244:8080/api/instruments
```

---

## Jak działa cykl minutowy

```
     :00        :05             :55        :00 (następna minuta)
      │          │               │          │
      ▼          ▼               ▼          ▼
  [Minuta T]  Oracle pobiera  Drużyny     Oracle pobiera
              cenę T, ocenia  wysyłają    cenę T+1, ocenia
              predykcje T-1   pred. dla T predykcje T
```

1. Oracle pobiera cenę co ~60 sekund.
2. Pobranie ceny w minucie **T** skutkuje oceną predykcji wysłanych w minucie **T-1**.
3. Drużyna powinna wysłać predykcję **jak najwcześniej po początku minuty** (najlepiej w pierwszych 30 sekundach).

---

## Topic z cenami: `market-prices`

Oracle publikuje ceny na topiku Kafki **`market-prices`** w formacie Protobuf.

**Broker:** `192.168.8.244:9092`

**Kiedy:** co ~60 sekund, dla każdego z 8 instrumentów osobno.

**Pola wiadomości:**

| Pole | Typ | Opis |
|------|-----|------|
| `symbol` | string | np. `BTC/USD` |
| `timestamp` | string | ISO-8601 UTC, np. `2026-04-08T20:05:00Z` |
| `interval` | string | zawsze `"1min"` |
| `open` | double | cena otwarcia tej świeczki |
| `high` | double | maksimum |
| `low` | double | minimum |
| `close` | double | cena zamknięcia tej świeczki |

> **Uwaga:** Cena `open` i `close` to wartości z dwóch kolejnych świeczek 1-minutowych — nie z bieżącej niekompletnej świeczki. Zapewnia to realne odchylenie (nie `open == close`).

---

## Schemat Protobuf

Schematy `.proto` są dostępne w osobnym repozytorium:

```bash
git clone git@git.corp.xtb.com:xserver-team/warsztaty/oracle-proto.git
```

---

## Jak wysyłać predykcje

### Topic: `predictions`

Wysyłaj wiadomości Protobuf na topic Kafki **`predictions`**.
Broker: `192.168.8.244:9092`

Klucz wiadomości Kafka (key): symbol instrumentu, np. `BTC/USD`.

### Timestamp — zasady

| Zasada | Szczegół |
|--------|----------|
| Strefa czasowa | **UTC** |
| Format | ISO-8601, np. `2026-04-08T20:05:00Z` |
| Wartość | Aktualna minuta zaokrąglona w dół (sekundy i milisekundy = 0) |
| Ważność | Pole **informacyjne** — Oracle przypisuje własny timestamp na podstawie czasu odbioru |

> **Serwer zawsze nadpisuje timestamp** wartością `floor(teraz / 60) * 60`. Nie możesz ustawić timestamp w przeszłości ani przyszłości — Oracle to wykryje i zaloguje ostrzeżenie.

Przykład w Pythonie:
```python
from datetime import datetime, timezone
now = datetime.now(timezone.utc)
timestamp = now.replace(second=0, microsecond=0).isoformat().replace('+00:00', 'Z')
# np. "2026-04-08T20:05:00Z"
```

---

## Walidacje — kiedy predykcja jest odrzucana

Oracle odrzuca predykcję (cicho, bez błędu Kafka) gdy:

| Powód | Przykład |
|-------|---------|
| `team` jest pusty lub sam białe znaki | `team = ""` lub `team = "  "` |
| `team` przekracza 30 znaków | `team = "nazwa-za-dluga-przekraczajaca-limit"` |
| `symbol` jest nieznany | `symbol = "DOGE/USD"` |
| `direction` ma brak wartości | pole 4 nieobecne w protobuf |
| Okno minuty zostało już ocenione | predykcja wysłana po pobraniu ceny dla tej minuty |
| Duplikat | ta sama drużyna + symbol + minuta już istnieje |

> Odrzucone predykcje nie wracają jako błąd Kafki — po prostu nie są zapisywane. Sprawdzaj stan przez API.

---

## Jak jest liczona skuteczność

### Reguła UP / DOWN

| Warunek | Wynik |
|---------|-------|
| `close > open` | wygrywa **UP** |
| `close < open` | wygrywa **DOWN** |
| `|close - open| < 0.0001` (remis) | wygrywa **UP** |

### Okno 100 minut

Skuteczność drużyny = liczba trafionych predykcji w **ostatnich 100 minutach**, w których Oracle opublikował cenę dla danego instrumentu.

```
Skuteczność = trafione / 100
```

**Brakująca predykcja** w danej minucie liczy się jako **błąd** — mianownik to zawsze 100.

Przykłady:
- Drużyna wysłała 1 predykcję i trafiła → **1%**
- Drużyna wysłała 100 predykcji i trafiła 60 → **60%**
- Drużyna przez 100 minut nic nie wysłała → **0%**
- Drużyna wysyłała przez 10 minut (10 pred.), trafiła 7 → **7%** (nie 70%!)

### Overall (ogólny ranking)

Skuteczność overall = suma trafionych predykcji we wszystkich instrumentach / 800
(8 instrumentów × 100 minut = 800 okienek łącznie).

---

## Minimalne przykładowe wysyłanie (Python)

```python
from datetime import datetime, timezone
from kafka import KafkaProducer

KAFKA = '192.168.8.244:9092'
TEAM  = 'nazwa-twojej-druzyny'  # max 30 znaków

def build_prediction(team, symbol, timestamp, direction):
    # Ręczny encoding protobuf (bez zewnętrznej biblioteki protobuf)
    def encode_str(field, val):
        b = val.encode('utf-8')
        return bytes([(field << 3) | 2, len(b)]) + b
    def encode_enum(field, val):
        return bytes([(field << 3) | 0, val])
    msg = bytearray()
    msg += encode_str(1, team)
    msg += encode_str(2, symbol)
    msg += encode_str(3, timestamp)
    msg += encode_enum(4, direction)
    return bytes(msg)

producer = KafkaProducer(bootstrap_servers=KAFKA, acks='all')

now = datetime.now(timezone.utc)
ts  = now.replace(second=0, microsecond=0).isoformat().replace('+00:00', 'Z')

# Wyślij predykcję UP dla BTC/USD
msg = build_prediction(TEAM, 'BTC/USD', ts, 0)  # 0 = UP, 1 = DOWN
producer.send('predictions', key=b'BTC/USD', value=msg)
producer.flush()
print(f"Wysłano: team={TEAM}, symbol=BTC/USD, direction=UP, ts={ts}")
```

---

## Sprawdzanie wyników

```bash
# Ranking ogólny
curl http://192.168.8.244:8080/api/ranking | jq .

# Ranking per instrument
curl "http://192.168.8.244:8080/api/ranking?symbol=BTC%2FUSD" | jq .

# Twoje wyniki szczegółowe
curl http://192.168.8.244:8080/api/ranking/nazwa-twojej-druzyny | jq .

# Twoje wyniki dla konkretnego instrumentu
curl "http://192.168.8.244:8080/api/ranking/nazwa-twojej-druzyny?symbol=BTC%2FUSD" | jq .

# Ostatnie ceny
curl http://192.168.8.244:8080/api/prices/latest | jq .

# Status serwisu
curl http://192.168.8.244:8080/api/status | jq .
```

---

## Dobre praktyki

- **Wysyłaj na początku minuty** — najlepiej w ciągu pierwszych 30 sekund. Predykcje wysłane po pobraniu ceny (ok. sekundy 60) mogą być odrzucone jako spóźnione.
- **Jedna predykcja na instrument na minutę** — duplikaty są ignorowane, liczy się tylko pierwsza.
- **Wysyłaj wszystkie 8 instrumentów** — brak predykcji dla instrumentu to stracone okienko (błąd w mianowniku).
- **Nie manipuluj timestampem** — serwer to wykrywa i loguje ostrzeżenie. Twój timestamp jest tylko informacyjny.
- **Subskrybuj `market-prices`** — możesz obserwować ceny i budować modele predykcyjne na ich podstawie (ale nie możesz już retroaktywnie wysłać predykcji dla minionej minuty).
- **Obserwuj ranking na żywo** — `http://192.168.8.244:8080/dashboard.html`

---
