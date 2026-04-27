# BAAM-price-seer

Hackathon project for the Oracle prediction game. See `docs/HACKATHON_PARTICIPANT_GUIDE.md`
and `CLAUDE.md` for full context.

## Backend (Kotlin / Spring Boot)

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew bootRun
```

## Frontend (React / Vite)

Live dashboard: rankings, per-instrument tiles, price timeline with prediction markers,
and a hype meter that crossfades audio tracks based on recent accuracy.

```bash
cd frontend
npm install        # first time only
npm run dev        # http://localhost:5173
```

The dev server proxies `/api/*` to `http://192.168.8.244:8080`, so you must be on the
hackathon network for live data.

Drop three looping MP3 files in `frontend/public/audio/` (`chill.mp3`, `hype.mp3`,
`max-hype.mp3`) to enable audio — see `frontend/public/audio/README.md`.
