## Summary

Brief summary of changes made to Kaziyami extensions.

## Source Information
- **Source Name**: 
- **Language**: `id` / `en`
- **Engine**: Generic / DooPlay / AnimeStream / RetroTube / Custom

## Invariants Checklist
- [ ] No `kaziyami`, `aniyomi`, `tachiyomi`, or `extension` strings in outgoing HTTP headers or URLs.
- [ ] Permanent Source ID matches `docs/SOURCE_IDS.md`.
- [ ] Uses `app.kaziyami.*` internal namespace.
- [ ] Does NOT modify `eu.kanade.tachiyomi.animesource.*` or `eu.kanade.tachiyomi.animeextension.*` loader contract.
- [ ] All unit tests pass (`./gradlew check`).
