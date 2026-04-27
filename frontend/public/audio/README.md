# Audio tracks

Place three looping MP3 files here:

- `chill.mp3` — low-energy background, plays when accuracy < 45% / no streak
- `hype.mp3` — mid-energy, plays at moderate accuracy or 2-3 hit streak
- `max-hype.mp3` — max-energy, plays at >75% recent accuracy or 4+ hit streak

The `useAudioCrossfade` hook crossfades between them based on `useHypeLevel`.

Free sources:
- https://pixabay.com/music/ (no attribution required)
- https://freesound.org/

Until files are present, audio toggle in `HypeMeter` will silently fail (the visual
intensity bar still works).
